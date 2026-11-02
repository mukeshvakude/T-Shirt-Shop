package com.shop.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shop.config.AppProperties;
import com.shop.dto.PageResponse;
import com.shop.dto.admin.AdminProductListItem;
import com.shop.dto.admin.AdminProductResponse;
import com.shop.dto.admin.AdminVariantResponse;
import com.shop.dto.admin.ImageUpdateRequest;
import com.shop.dto.admin.ProductRequest;
import com.shop.dto.admin.StockUpdateRequest;
import com.shop.dto.admin.VariantRequest;
import com.shop.dto.admin.VariantsUpdateRequest;
import com.shop.dto.product.ProductImageResponse;
import com.shop.entity.Category;
import com.shop.entity.Inventory;
import com.shop.entity.Product;
import com.shop.entity.ProductColor;
import com.shop.entity.ProductImage;
import com.shop.entity.ProductSize;
import com.shop.entity.ProductStatus;
import com.shop.entity.SizeGroup;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ConflictException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.ProductMapper;
import com.shop.repository.CategoryRepository;
import com.shop.repository.InventoryRepository;
import com.shop.repository.ProductColorRepository;
import com.shop.repository.ProductImageRepository;
import com.shop.repository.ProductRepository;
import com.shop.repository.ProductSearchCriteria;
import com.shop.repository.ProductSizeRepository;
import com.shop.service.AdminProductService;
import com.shop.service.StorageService;
import com.shop.util.ImageValidator;
import com.shop.util.SlugUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductServiceImpl implements AdminProductService {

    private static final Logger log = LoggerFactory.getLogger(AdminProductServiceImpl.class);
    static final int MAX_IMAGES_PER_PRODUCT = 12;
    private static final String CUSTOM_CATEGORY = "CUSTOM_TSHIRTS";
    private static final String PANTS_CATEGORY = "PANTS";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSizeRepository sizeRepository;
    private final ProductColorRepository colorRepository;
    private final StorageService storageService;
    private final ProductMapper mapper;
    private final AppProperties appProperties;

    // ------------------------------------------------------------------ products

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProductListItem> list(ProductSearchCriteria criteria, int page, int size) {
        Page<Product> result = productRepository.search(criteria, PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, List<ProductImage>> images = ids.isEmpty() ? Map.of()
                : imageRepository.findForProducts(ids).stream().collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        Map<Long, List<Inventory>> variants = ids.isEmpty() ? Map.of()
                : inventoryRepository.findActiveForProducts(ids).stream()
                        .collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        List<AdminProductListItem> content = result.getContent().stream()
                .map(p -> mapper.toAdminListItem(p, images.getOrDefault(p.getId(), List.of()),
                        variants.getOrDefault(p.getId(), List.of())))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        return toAdmin(findProduct(id));
    }

    @Override
    public AdminProductResponse create(ProductRequest request) {
        Category category = findCategory(request.categoryId());
        validatePricesAndCategory(request, category);
        if (request.status() == ProductStatus.ACTIVE) {
            throw notPublishable();
        }
        Product product = new Product();
        product.setSlug(resolveSlug(request.slug(), request.name(), null));
        apply(product, request, category);
        productRepository.save(product);
        log.info("Admin created product {} ({})", product.getId(), product.getSlug());
        return toAdmin(product);
    }

    @Override
    public AdminProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        Category category = findCategory(request.categoryId());
        validatePricesAndCategory(request, category);
        if (!category.getId().equals(product.getCategory().getId())) {
            requireCompatibleSizes(product.getId(), category);
        }
        if (request.status() == ProductStatus.ACTIVE && !hasActiveVariant(product.getId())) {
            throw notPublishable();
        }
        product.setSlug(resolveSlug(request.slug(), request.name(), product));
        apply(product, request, category);
        return toAdmin(product);
    }

    @Override
    public AdminProductResponse changeStatus(Long id, ProductStatus status) {
        Product product = findProduct(id);
        if (status == ProductStatus.ACTIVE && !hasActiveVariant(id)) {
            throw notPublishable();
        }
        product.setStatus(status);
        return toAdmin(product);
    }

    @Override
    public void delete(Long id) {
        Product product = findProduct(id);
        if (productRepository.countOrderItems(id) > 0) {
            throw new ConflictException("PRODUCT_HAS_ORDERS",
                    "This product appears in orders and cannot be deleted. Archive it instead.");
        }
        List<String> storedKeys = product.getImages().stream()
                .map(ProductImage::getStorageKey)
                .filter(k -> k != null)
                .toList();
        inventoryRepository.deleteAll(inventoryRepository.findAllForProduct(id));
        productRepository.delete(product);
        productRepository.flush();
        afterCommit(() -> storedKeys.forEach(storageService::delete));
        log.info("Admin deleted product {}", id);
    }

    // ------------------------------------------------------------------ variants and stock

    @Override
    public List<AdminVariantResponse> replaceVariants(Long productId, VariantsUpdateRequest request) {
        Product product = findProduct(productId);
        SizeGroup expectedGroup = sizeGroupFor(product.getCategory());

        Map<Long, ProductSize> sizes = sizeRepository.findAllById(
                request.variants().stream().map(VariantRequest::sizeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ProductSize::getId, s -> s));
        Map<Long, ProductColor> colors = colorRepository.findAllById(
                request.variants().stream().map(VariantRequest::colorId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ProductColor::getId, c -> c));

        Map<String, Inventory> existing = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findAllForProduct(productId)) {
            existing.put(key(inventory.getSize().getId(), inventory.getColor().getId()), inventory);
        }

        Set<String> requested = new HashSet<>();
        for (VariantRequest v : request.variants()) {
            ProductSize size = sizes.get(v.sizeId());
            ProductColor color = colors.get(v.colorId());
            if (size == null || color == null) {
                throw new BadRequestException("UNKNOWN_SIZE_OR_COLOR", "Unknown size or colour in the variant list.");
            }
            if (size.getSizeGroup() != expectedGroup) {
                throw new BadRequestException("SIZE_NOT_ALLOWED", "Size " + size.getCode() + " cannot be used for "
                        + product.getCategory().getName() + ". Use " + expectedGroup.name().toLowerCase() + " sizes.");
            }
            String key = key(size.getId(), color.getId());
            if (!requested.add(key)) {
                throw new BadRequestException("DUPLICATE_VARIANT",
                        "Size " + size.getCode() + " / " + color.getCode() + " is listed twice.");
            }
            Inventory inventory = existing.get(key);
            if (inventory == null) {
                inventory = new Inventory();
                inventory.setProduct(product);
                inventory.setSize(size);
                inventory.setColor(color);
                inventory.setSku(uniqueSku(product, size, color));
                existing.put(key, inventory);
            } else if (v.quantityOnHand() < inventory.getQuantityReserved()) {
                throw stockBelowReserved(inventory);
            }
            inventory.setQuantityOnHand(v.quantityOnHand());
            inventory.setLowStockThreshold(v.lowStockThreshold());
            inventory.setActive(v.active());
            inventoryRepository.save(inventory);
        }

        existing.forEach((key, inventory) -> {
            if (!requested.contains(key)) {
                inventory.setActive(false);
            }
        });
        inventoryRepository.flush();

        if (product.getStatus() == ProductStatus.ACTIVE && !hasActiveVariant(productId)) {
            throw new BusinessRuleException("PRODUCT_NEEDS_VARIANT",
                    "An active product needs at least one active size and colour. Deactivate the product first.");
        }
        return inventoryRepository.findAllForProduct(productId).stream().map(mapper::toAdminVariant).toList();
    }

    @Override
    public AdminVariantResponse updateStock(Long inventoryId, StockUpdateRequest request) {
        if (request.quantityOnHand() != null && request.adjustBy() != null) {
            throw new BadRequestException("STOCK_UPDATE_AMBIGUOUS", "Send either quantityOnHand or adjustBy, not both.");
        }
        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", inventoryId));
        int newQuantity = inventory.getQuantityOnHand();
        if (request.quantityOnHand() != null) {
            newQuantity = request.quantityOnHand();
        } else if (request.adjustBy() != null) {
            newQuantity = inventory.getQuantityOnHand() + request.adjustBy();
        }
        if (newQuantity < 0) {
            throw new BusinessRuleException("STOCK_NEGATIVE", "Stock cannot go below zero.");
        }
        if (newQuantity < inventory.getQuantityReserved()) {
            throw stockBelowReserved(inventory);
        }
        inventory.setQuantityOnHand(newQuantity);
        if (request.lowStockThreshold() != null) {
            inventory.setLowStockThreshold(request.lowStockThreshold());
        }
        if (request.active() != null) {
            inventory.setActive(request.active());
        }
        log.info("Stock for {} set to {} (reserved {})", inventory.getSku(), newQuantity, inventory.getQuantityReserved());
        return mapper.toAdminVariant(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminVariantResponse> lowStock(int page, int size) {
        return PageResponse.of(inventoryRepository.findLowStock(PageRequest.of(page, size)), mapper::toAdminVariant);
    }

    // ------------------------------------------------------------------ images

    @Override
    public ProductImageResponse addImage(Long productId, byte[] content, Long colorId, String altText) {
        Product product = findProduct(productId);
        if (product.getImages().size() >= MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessRuleException("TOO_MANY_IMAGES",
                    "A product can have at most " + MAX_IMAGES_PER_PRODUCT + " images.");
        }
        ImageValidator.ValidatedImage image = ImageValidator.validate(content,
                appProperties.storage().maxImageBytes(), appProperties.storage().maxImageDimension());

        StorageService.StoredFile stored = storageService.store("products", image.content(), image.type().extension());
        afterRollback(() -> storageService.delete(stored.key()));

        ProductImage entity = new ProductImage();
        entity.setProduct(product);
        entity.setColor(colorId != null ? findColor(colorId) : null);
        entity.setImageUrl(stored.url());
        entity.setStorageKey(stored.key());
        entity.setAltText(altText == null || altText.isBlank() ? product.getName() : altText.trim());
        entity.setSortOrder(product.getImages().stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1);
        entity.setPrimaryImage(product.getImages().stream().noneMatch(ProductImage::isPrimaryImage));
        product.getImages().add(entity);
        imageRepository.save(entity);
        return mapper.toImage(entity);
    }

    @Override
    public ProductImageResponse updateImage(Long productId, Long imageId, ImageUpdateRequest request) {
        Product product = findProduct(productId);
        ProductImage image = findImage(product, imageId);
        if (request.altText() != null) {
            image.setAltText(request.altText().isBlank() ? product.getName() : request.altText().trim());
        }
        image.setColor(request.colorId() != null ? findColor(request.colorId()) : null);
        if (request.sortOrder() != null) {
            image.setSortOrder(request.sortOrder());
        }
        if (Boolean.TRUE.equals(request.primary())) {
            product.getImages().forEach(i -> i.setPrimaryImage(i.getId().equals(imageId)));
        }
        return mapper.toImage(image);
    }

    @Override
    public void deleteImage(Long productId, Long imageId) {
        Product product = findProduct(productId);
        ProductImage image = findImage(product, imageId);
        boolean wasPrimary = image.isPrimaryImage();
        String storageKey = image.getStorageKey();
        product.getImages().remove(image);
        if (wasPrimary && !product.getImages().isEmpty()) {
            product.getImages().get(0).setPrimaryImage(true);
        }
        if (storageKey != null) {
            afterCommit(() -> storageService.delete(storageKey));
        }
    }

    // ------------------------------------------------------------------ helpers

    private AdminProductResponse toAdmin(Product product) {
        List<Long> ids = List.of(product.getId());
        List<ProductImage> images = imageRepository.findForProducts(ids);
        return mapper.toAdmin(product, images, inventoryRepository.findAllForProduct(product.getId()));
    }

    private void apply(Product product, ProductRequest request, Category category) {
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setShortDescription(blankToNull(request.shortDescription()));
        product.setDescription(blankToNull(request.description()));
        product.setBasePrice(request.basePrice());
        product.setDiscountPrice(request.discountPrice());
        product.setStatus(request.status());
        product.setFeatured(request.featured());
        product.setNewArrival(request.newArrival());
        product.setCustomizable(request.customizable());
        product.setMaterial(blankToNull(request.material()));
        product.setFit(blankToNull(request.fit()));
        product.setCareInstructions(blankToNull(request.careInstructions()));
    }

    private void validatePricesAndCategory(ProductRequest request, Category category) {
        if (request.discountPrice() != null && request.discountPrice().compareTo(request.basePrice()) >= 0) {
            throw new BadRequestException("DISCOUNT_NOT_LOWER", "The discount price must be lower than the base price.");
        }
        boolean customCategory = CUSTOM_CATEGORY.equals(category.getCode());
        if (request.customizable() != customCategory) {
            throw new BadRequestException("CUSTOMIZABLE_MISMATCH",
                    "Only products in the Customized T-Shirts category can be customizable, and all of them must be.");
        }
    }

    private void requireCompatibleSizes(Long productId, Category newCategory) {
        SizeGroup group = sizeGroupFor(newCategory);
        boolean incompatible = inventoryRepository.findAllForProduct(productId).stream()
                .anyMatch(i -> i.getSize().getSizeGroup() != group);
        if (incompatible) {
            throw new BusinessRuleException("CATEGORY_SIZE_MISMATCH",
                    "This product has sizes that do not fit the new category. Change its sizes first.");
        }
    }

    private boolean hasActiveVariant(Long productId) {
        return inventoryRepository.findAllForProduct(productId).stream().anyMatch(Inventory::isActive);
    }

    private String resolveSlug(String requested, String name, Product current) {
        Long currentId = current != null ? current.getId() : null;
        if (requested != null && !requested.isBlank()) {
            boolean taken = currentId == null ? productRepository.existsBySlug(requested)
                    : productRepository.existsBySlugAndIdNot(requested, currentId);
            if (taken) {
                throw new ConflictException("SLUG_TAKEN", "Another product already uses the URL name '" + requested + "'.");
            }
            return requested;
        }
        if (current != null) {
            return current.getSlug();
        }
        String base = SlugUtils.slugify(name);
        String slug = base;
        for (int i = 2; productRepository.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private String uniqueSku(Product product, ProductSize size, ProductColor color) {
        String prefix = product.getCategory().getCode().length() >= 2 ? product.getCategory().getCode().substring(0, 2) : "PR";
        String base = String.format("%s-%05d-%s-%s", prefix, product.getId(), color.getCode(), size.getCode());
        String sku = base;
        for (int i = 2; inventoryRepository.existsBySku(sku); i++) {
            sku = base + "-" + i;
        }
        return sku;
    }

    static SizeGroup sizeGroupFor(Category category) {
        return PANTS_CATEGORY.equals(category.getCode()) ? SizeGroup.WAIST : SizeGroup.APPAREL;
    }

    private Product findProduct(Long id) {
        return productRepository.findWithCategoryById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new BadRequestException("UNKNOWN_CATEGORY",
                "Category " + id + " does not exist."));
    }

    private ProductColor findColor(Long id) {
        return colorRepository.findById(id).orElseThrow(() -> new BadRequestException("UNKNOWN_COLOR",
                "Colour " + id + " does not exist."));
    }

    private static ProductImage findImage(Product product, Long imageId) {
        return product.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image", imageId));
    }

    private static String key(Long sizeId, Long colorId) {
        return sizeId + ":" + colorId;
    }

    private static BusinessRuleException notPublishable() {
        return new BusinessRuleException("PRODUCT_NOT_PUBLISHABLE",
                "Add at least one active size and colour before setting the product to ACTIVE.");
    }

    private static BusinessRuleException stockBelowReserved(Inventory inventory) {
        return new BusinessRuleException("STOCK_BELOW_RESERVED", "Stock for " + inventory.getSku()
                + " cannot be lower than the " + inventory.getQuantityReserved() + " units reserved by pending orders.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        action.run();
                    }
                }
            });
        }
    }
}
