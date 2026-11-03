package com.shop.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.shop.entity.Category;
import com.shop.entity.Inventory;
import com.shop.entity.Product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Criteria-API implementation of the catalog search. Runs one count query and one
 * page query; the category is fetched in the page query to avoid N+1 selects.
 */
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    private static final int MAX_QUERY_LENGTH = 100;

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        Join<Product, Category> countCategory = countRoot.join("category");
        countQuery.select(cb.count(countRoot))
                .where(predicates(cb, countQuery, countRoot, countCategory, criteria).toArray(Predicate[]::new));
        long total = em.createQuery(countQuery).getSingleResult();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Category> category = (Join<Product, Category>) (Join<?, ?>) product.fetch("category");
        query.select(product)
                .where(predicates(cb, query, product, category, criteria).toArray(Predicate[]::new))
                .orderBy(orders(cb, product, criteria.sortOrDefault()));
        List<Product> content = em.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Product> product,
            Join<Product, Category> category, ProductSearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();
        Expression<BigDecimal> price = effectivePrice(cb, product);

        if (c.statuses() != null && !c.statuses().isEmpty()) {
            predicates.add(product.get("status").in(c.statuses()));
        }
        if (c.activeCategoryOnly()) {
            predicates.add(cb.isTrue(category.get("active")));
        }
        if (c.categoryId() != null) {
            predicates.add(cb.equal(category.get("id"), c.categoryId()));
        }
        if (hasText(c.categorySlug())) {
            predicates.add(cb.equal(category.get("slug"), c.categorySlug()));
        }
        if (hasText(c.query())) {
            String pattern = "%" + escapeLike(c.query().trim().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(product.get("name")), pattern, '\\'),
                    cb.like(cb.lower(product.get("shortDescription")), pattern, '\\'),
                    cb.like(cb.lower(category.get("name")), pattern, '\\')));
        }
        if (c.minPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(price, c.minPrice()));
        }
        if (c.maxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(price, c.maxPrice()));
        }
        if (c.featured() != null) {
            predicates.add(cb.equal(product.get("featured"), c.featured()));
        }
        if (c.newArrival() != null) {
            predicates.add(cb.equal(product.get("newArrival"), c.newArrival()));
        }
        if (Boolean.TRUE.equals(c.onSale())) {
            predicates.add(cb.isNotNull(product.get("discountPrice")));
        }

        boolean filterVariants = notEmpty(c.sizeCodes()) || notEmpty(c.colorCodes()) || c.inStockOnly();
        if (filterVariants) {
            // There must be one active, in-stock variant matching the chosen size(s) AND colour(s).
            Subquery<Long> variant = query.subquery(Long.class);
            Root<Inventory> inventory = variant.from(Inventory.class);
            List<Predicate> variantPredicates = new ArrayList<>();
            variantPredicates.add(cb.equal(inventory.get("product"), product));
            variantPredicates.add(cb.isTrue(inventory.get("active")));
            variantPredicates.add(cb.greaterThan(
                    cb.diff(inventory.<Integer>get("quantityOnHand"), inventory.<Integer>get("quantityReserved")), 0));
            if (notEmpty(c.sizeCodes())) {
                variantPredicates.add(inventory.get("size").get("code").in(c.sizeCodes()));
            }
            if (notEmpty(c.colorCodes())) {
                variantPredicates.add(inventory.get("color").get("code").in(c.colorCodes()));
            }
            variant.select(cb.literal(1L)).where(variantPredicates.toArray(Predicate[]::new));
            predicates.add(cb.exists(variant));
        }
        return predicates;
    }

    private static List<Order> orders(CriteriaBuilder cb, Root<Product> product, ProductSort sort) {
        Expression<BigDecimal> price = effectivePrice(cb, product);
        Order tieBreak = cb.desc(product.get("id"));
        return switch (sort) {
            case PRICE_ASC -> List.of(cb.asc(price), tieBreak);
            case PRICE_DESC -> List.of(cb.desc(price), tieBreak);
            case POPULAR -> List.of(cb.desc(product.get("soldCount")), tieBreak);
            case RATING -> List.of(cb.desc(product.get("avgRating")), cb.desc(product.get("reviewCount")), tieBreak);
            case NAME_ASC -> List.of(cb.asc(product.get("name")), tieBreak);
            case UPDATED_DESC -> List.of(cb.desc(product.get("updatedAt")), tieBreak);
            case NEWEST -> List.of(cb.desc(product.get("createdAt")), tieBreak);
        };
    }

    private static Expression<BigDecimal> effectivePrice(CriteriaBuilder cb, Root<Product> product) {
        return cb.coalesce(product.<BigDecimal>get("discountPrice"), product.<BigDecimal>get("basePrice"));
    }

    private static String escapeLike(String value) {
        String trimmed = value.length() > MAX_QUERY_LENGTH ? value.substring(0, MAX_QUERY_LENGTH) : value;
        return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
    }
}
