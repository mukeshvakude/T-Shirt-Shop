package com.shop.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.cart.AddCartItemRequest;
import com.shop.dto.cart.CartResponse;
import com.shop.dto.cart.GuestCartRequest;
import com.shop.dto.cart.UpdateCartItemRequest;
import com.shop.dto.pricing.PriceQuote;
import com.shop.entity.Cart;
import com.shop.entity.CartItem;
import com.shop.entity.Coupon;
import com.shop.entity.CustomDesign;
import com.shop.entity.Inventory;
import com.shop.entity.Product;
import com.shop.entity.ProductImage;
import com.shop.entity.ProductStatus;
import com.shop.exception.ApiException;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.CartRepository;
import com.shop.repository.CouponRepository;
import com.shop.repository.CustomDesignRepository;
import com.shop.repository.InventoryRepository;
import com.shop.repository.ProductImageRepository;
import com.shop.service.AppSettingService;
import com.shop.service.CartService;
import com.shop.service.PricingService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cart rules: prices, stock and coupons are always worked out here from current data. A line
 * that cannot be bought right now stays in the cart with a message but is not charged, and
 * checkout stays blocked until the customer fixes it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    static final int MAX_LINES = 50;
    static final String MAX_QTY = "cart.max_quantity_per_item";
    static final String SHIPPING_FLAT = "shipping.flat_charge";
    static final String SHIPPING_FREE = "shipping.free_threshold";

    private final CartRepository carts;
    private final InventoryRepository inventory;
    private final ProductImageRepository images;
    private final CustomDesignRepository designs;
    private final CouponRepository coupons;
    private final PricingService pricing;
    private final AppSettingService settings;
    private final JsonMapper json;
    private final Clock clock;

    // ------------------------------------------------------------------ customer cart

    @Override
    @Transactional(readOnly = true)
    public CartResponse get(Long userId) {
        return carts.findByUserIdWithItems(userId)
                .map(c -> view(c.getItems(), c.getCouponCode(), userId, List.of()))
                .orElseGet(() -> view(List.of(), null, userId, List.of()));
    }

    @Override
    public CartResponse add(Long userId, AddCartItemRequest r) {
        if ((r.sku() == null) == (r.designCode() == null)) {
            throw new BadRequestException("ITEM_REQUIRED", "Choose a product or one of your designs to add.");
        }
        Cart cart = cartFor(userId);
        CustomDesign design = null;
        Inventory inv;
        if (r.designCode() != null) {
            design = designs.findByCode(r.designCode())
                    .filter(d -> d.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Design", r.designCode()));
            requireSellable(design.getBaseProduct(), true);
            String size = r.sizeCode() != null ? r.sizeCode() : design.getSizeCode();
            inv = variant(design.getBaseProduct(), design.getColorCode(), size)
                    .orElseThrow(() -> new BadRequestException("VARIANT_UNAVAILABLE", "This size is not available for your design."));
        } else {
            inv = inventory.findBySkuWithDetails(r.sku())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", r.sku()));
            requireSellable(inv.getProduct(), false);
            if (!inv.isActive()) {
                throw new BusinessRuleException("UNAVAILABLE", "This size and colour is no longer sold.");
            }
        }
        List<String> notices = new ArrayList<>();
        addLine(cart, inv, design, r.quantity(), notices, true);
        carts.saveAndFlush(cart);
        return view(cart.getItems(), cart.getCouponCode(), userId, notices);
    }

    @Override
    public CartResponse update(Long userId, Long itemId, UpdateCartItemRequest r) {
        Cart cart = existingCart(userId);
        CartItem item = line(cart, itemId);
        int maxQty = maxQuantity();
        int quantity = r.quantity() != null ? r.quantity() : item.getQuantity();
        if (quantity > maxQty) {
            throw new BadRequestException("INVALID_QUANTITY", "You can buy up to " + maxQty + " of each item.");
        }
        List<String> notices = new ArrayList<>();
        Inventory target = item.getInventory();
        if (r.sizeCode() != null || r.colorCode() != null) {
            String currentColor = target.getColor().getCode();
            if (item.isCustom() && r.colorCode() != null && !r.colorCode().equals(currentColor)) {
                throw new BadRequestException("COLOR_FIXED", "To change the colour of a custom T-Shirt, edit the design.");
            }
            String color = r.colorCode() != null ? r.colorCode() : currentColor;
            String size = r.sizeCode() != null ? r.sizeCode() : target.getSize().getCode();
            target = variant(target.getProduct(), color, size)
                    .orElseThrow(() -> new BadRequestException("VARIANT_UNAVAILABLE", "That size and colour is not available."));
        }
        boolean merged = false;
        if (!target.getId().equals(item.getInventory().getId())) {
            Inventory newVariant = target;
            CartItem same = cart.getItems().stream()
                    .filter(i -> i != item && i.sameLineAs(newVariant, item.getCustomDesign()))
                    .findFirst().orElse(null);
            if (same != null) {
                quantity += same.getQuantity();
                cart.getItems().remove(same);
                merged = true;
            }
            item.setInventory(target);
        }
        int available = target.getAvailable() - otherQuantity(cart, item, target);
        if (available <= 0) {
            throw new BusinessRuleException("OUT_OF_STOCK", "This size and colour is out of stock.");
        }
        if (merged) {
            int capped = Math.min(quantity, Math.min(maxQty, available));
            if (capped < quantity) {
                notices.add("Combined with the same item already in your bag; quantity set to " + capped + ".");
            }
            quantity = capped;
        } else if (quantity > available) {
            throw new BusinessRuleException("NOT_ENOUGH_STOCK", "Only " + available + " left in this size and colour.");
        }
        item.setQuantity(quantity);
        carts.saveAndFlush(cart);
        return view(cart.getItems(), cart.getCouponCode(), userId, notices);
    }

    @Override
    public CartResponse remove(Long userId, Long itemId) {
        Cart cart = existingCart(userId);
        cart.getItems().remove(line(cart, itemId));
        carts.saveAndFlush(cart);
        return view(cart.getItems(), cart.getCouponCode(), userId, List.of());
    }

    @Override
    public CartResponse clear(Long userId) {
        Optional<Cart> cart = carts.findByUserIdWithItems(userId);
        cart.ifPresent(c -> {
            c.getItems().clear();
            c.setCouponCode(null);
            carts.saveAndFlush(c);
        });
        return view(List.of(), null, userId, List.of());
    }

    @Override
    public CartResponse merge(Long userId, GuestCartRequest request) {
        Cart cart = cartFor(userId);
        List<String> notices = new ArrayList<>();
        for (GuestCartRequest.Item it : request.items()) {
            Optional<Inventory> found = inventory.findBySkuWithDetails(it.sku());
            if (found.isEmpty() || !found.get().isActive() || !sellable(found.get().getProduct())
                    || found.get().getProduct().isCustomizable()) {
                notices.add("An item from your bag is no longer available and was not added.");
                continue;
            }
            addLine(cart, found.get(), null, it.quantity(), notices, false);
        }
        carts.saveAndFlush(cart);
        return view(cart.getItems(), cart.getCouponCode(), userId, notices);
    }

    @Override
    public CartResponse applyCoupon(Long userId, String rawCode) {
        Cart cart = cartFor(userId);
        String code = rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        CartResponse withoutCoupon = view(cart.getItems(), null, userId, List.of());
        if (withoutCoupon.subtotal().signum() == 0) {
            throw new BusinessRuleException("CART_EMPTY", "Add items to your bag before applying a coupon.");
        }
        Coupon coupon = evaluate(code, withoutCoupon.subtotal(), userId).coupon();
        cart.setCouponCode(coupon.getCode());
        carts.saveAndFlush(cart);
        return view(cart.getItems(), cart.getCouponCode(), userId, List.of());
    }

    @Override
    public CartResponse removeCoupon(Long userId) {
        Cart cart = cartFor(userId);
        cart.setCouponCode(null);
        carts.saveAndFlush(cart);
        return view(cart.getItems(), null, userId, List.of());
    }

    // ------------------------------------------------------------------ guests

    @Override
    @Transactional(readOnly = true)
    public CartResponse quote(GuestCartRequest request) {
        Cart transientCart = new Cart();
        List<String> notices = new ArrayList<>();
        for (GuestCartRequest.Item it : request.items()) {
            Optional<Inventory> found = inventory.findBySkuWithDetails(it.sku());
            if (found.isEmpty() || found.get().getProduct().isCustomizable()) {
                notices.add("An item in your bag is no longer sold.");
                continue;
            }
            CartItem existing = transientCart.getItems().stream()
                    .filter(i -> i.sameLineAs(found.get(), null)).findFirst().orElse(null);
            if (existing != null) {
                existing.setQuantity(Math.min(existing.getQuantity() + it.quantity(), maxQuantity()));
            } else {
                CartItem item = new CartItem();
                item.setInventory(found.get());
                item.setQuantity(Math.min(it.quantity(), maxQuantity()));
                transientCart.getItems().add(item);
            }
        }
        return view(transientCart.getItems(), null, null, notices);
    }

    // ------------------------------------------------------------------ lines

    /**
     * Adds quantity to the matching line (or a new line). Strict mode (customer action) fails with
     * a clear message; lenient mode (merging a guest bag) caps the quantity and adds a notice.
     */
    private void addLine(Cart cart, Inventory inv, CustomDesign design, int quantity, List<String> notices, boolean strict) {
        int maxQty = maxQuantity();
        String name = inv.getProduct().getName();
        CartItem existing = cart.getItems().stream().filter(i -> i.sameLineAs(inv, design)).findFirst().orElse(null);
        int current = existing == null ? 0 : existing.getQuantity();
        int available = inv.getAvailable() - otherQuantity(cart, existing, inv);
        int allowed = Math.min(maxQty, available);
        if (available <= 0) {
            fail(strict, notices, "OUT_OF_STOCK", name + " (" + variantLabel(inv) + ") is out of stock.");
            return;
        }
        if (allowed <= current) {
            fail(strict, notices, "QUANTITY_LIMIT", current >= maxQty
                    ? "You already have the most you can buy of " + name + " (" + maxQty + ") in your bag."
                    : "All " + available + " available of " + name + " (" + variantLabel(inv) + ") are already in your bag.");
            return;
        }
        int wanted = current + quantity;
        int finalQty = Math.min(wanted, allowed);
        if (finalQty < wanted) {
            notices.add("Only " + (finalQty - current) + " more of " + name + " (" + variantLabel(inv) + ") could be added.");
        }
        if (existing != null) {
            existing.setQuantity(finalQty);
            return;
        }
        if (cart.getItems().size() >= MAX_LINES) {
            fail(strict, notices, "CART_FULL", "Your bag can hold up to " + MAX_LINES + " different items.");
            return;
        }
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setInventory(inv);
        item.setCustomDesign(design);
        item.setQuantity(finalQty);
        cart.getItems().add(item);
    }

    private static void fail(boolean strict, List<String> notices, String code, String message) {
        if (strict) {
            throw new BusinessRuleException(code, message);
        }
        notices.add(message);
    }

    /** Quantity of the same SKU in other lines (for example the same blank T-Shirt with two designs). */
    private static int otherQuantity(Cart cart, CartItem except, Inventory inv) {
        return cart.getItems().stream()
                .filter(i -> i != except && i.getInventory().getId().equals(inv.getId()))
                .mapToInt(CartItem::getQuantity).sum();
    }

    private Optional<Inventory> variant(Product product, String colorCode, String sizeCode) {
        return inventory.findActiveForProducts(List.of(product.getId())).stream()
                .filter(i -> i.getColor().getCode().equals(colorCode) && i.getSize().getCode().equals(sizeCode))
                .findFirst();
    }

    private void requireSellable(Product product, boolean custom) {
        if (!sellable(product)) {
            throw new BusinessRuleException("UNAVAILABLE", product.getName() + " is not available right now.");
        }
        if (!custom && product.isCustomizable()) {
            throw new BusinessRuleException("DESIGN_REQUIRED", "Design your T-Shirt first, then add it to your bag.");
        }
    }

    private static boolean sellable(Product product) {
        return product.getStatus() == ProductStatus.ACTIVE && product.getCategory().isActive();
    }

    private Cart cartFor(Long userId) {
        return carts.findByUserIdWithItems(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUserId(userId);
            return carts.saveAndFlush(cart);
        });
    }

    private Cart existingCart(Long userId) {
        return carts.findByUserIdWithItems(userId).orElseThrow(() -> new ResourceNotFoundException("Cart item", userId));
    }

    private static CartItem line(Cart cart, Long itemId) {
        return cart.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
    }

    // ------------------------------------------------------------------ pricing

    private record Evaluated(Coupon coupon, BigDecimal discount) {
    }

    /** Checks a coupon against the current subtotal and the customer's past use. */
    private Evaluated evaluate(String code, BigDecimal subtotal, Long userId) {
        Instant now = clock.instant();
        Coupon c = coupons.findByCode(code).filter(Coupon::isActive)
                .orElseThrow(() -> new BusinessRuleException("COUPON_NOT_FOUND", "The coupon " + code + " is not valid."));
        if (now.isBefore(c.getValidFrom())) {
            throw new BusinessRuleException("COUPON_NOT_STARTED", "The coupon " + code + " is not active yet.");
        }
        if (!now.isBefore(c.getValidTo())) {
            throw new BusinessRuleException("COUPON_EXPIRED", "The coupon " + code + " has expired.");
        }
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            throw new BusinessRuleException("COUPON_USED_UP", "The coupon " + code + " has been fully used.");
        }
        if (userId != null && coupons.countRedemptions(c.getId(), userId) >= c.getPerUserLimit()) {
            throw new BusinessRuleException("COUPON_ALREADY_USED", c.getPerUserLimit() == 1
                    ? "You have already used the coupon " + code + "."
                    : "You have used the coupon " + code + " the maximum " + c.getPerUserLimit() + " times.");
        }
        if (subtotal.compareTo(c.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException("COUPON_MIN_ORDER", "Add " + rupees(c.getMinOrderAmount().subtract(subtotal))
                    + " more to use " + code + " (minimum order " + rupees(c.getMinOrderAmount()) + ").");
        }
        BigDecimal discount = c.getDiscountType() == Coupon.DiscountType.PERCENT
                ? subtotal.multiply(c.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : c.getDiscountValue();
        if (c.getMaxDiscount() != null) {
            discount = discount.min(c.getMaxDiscount());
        }
        return new Evaluated(c, money(discount.min(subtotal)));
    }

    private CartResponse view(List<CartItem> items, String couponCode, Long userId, List<String> notices) {
        int maxQty = maxQuantity();
        List<Long> productIds = items.stream().map(i -> i.getInventory().getProduct().getId()).distinct().toList();
        Map<Long, List<ProductImage>> imagesByProduct = productIds.isEmpty() ? Map.of()
                : images.findForProducts(productIds).stream().collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        Map<Long, List<Inventory>> variantsByProduct = productIds.isEmpty() ? Map.of()
                : inventory.findActiveForProducts(productIds).stream().collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        Map<Long, Integer> usedPerSku = new HashMap<>();
        items.forEach(i -> usedPerSku.merge(i.getInventory().getId(), i.getQuantity(), Integer::sum));

        List<CartResponse.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        boolean allOk = !items.isEmpty();
        for (CartItem item : items) {
            Inventory inv = item.getInventory();
            Product p = inv.getProduct();
            CustomDesign design = item.getCustomDesign();
            int available = inv.getAvailable();
            int usedHere = usedPerSku.get(inv.getId());

            String status;
            String message = null;
            if (!sellable(p) || !inv.isActive()) {
                status = "UNAVAILABLE";
                message = "No longer available. Please remove it.";
            } else if (available == 0) {
                status = "OUT_OF_STOCK";
                message = "Out of stock in this size and colour.";
            } else if (usedHere > available) {
                status = "NOT_ENOUGH_STOCK";
                message = "Only " + available + " left in this size and colour. Reduce the quantity.";
            } else if (inv.isLowStock()) {
                status = "LOW_STOCK";
                message = "Only " + available + " left.";
            } else {
                status = "OK";
            }
            boolean buyable = status.equals("OK") || status.equals("LOW_STOCK");

            BigDecimal unit;
            BigDecimal mrp;
            CartResponse.Design designInfo = null;
            if (design != null) {
                PriceQuote q = pricing.quote(design, inv.getSize().getCode(), Math.min(item.getQuantity(), maxQty));
                unit = q.unitPrice();
                mrp = money(q.baseMrp().add(q.customizationPerUnit()));
                designInfo = new CartResponse.Design(design.getDesignCode(), design.getName(), media(design.getPreviewFrontKey()),
                        media(design.getPreviewBackKey()), list(design.getPlacements()), design.isPremiumDesign(),
                        texts(design.getTextItems()), q.basePrice(), q.charges());
            } else {
                unit = money(p.getEffectivePrice());
                mrp = money(p.getBasePrice());
            }
            BigDecimal lineTotal = money(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
            if (buyable) {
                subtotal = subtotal.add(lineTotal);
            } else {
                allOk = false;
            }
            count += item.getQuantity();

            int roomForThisLine = available - (usedHere - item.getQuantity());
            int lineMax = Math.max(0, Math.min(maxQty, roomForThisLine));
            List<CartResponse.Variant> variants = variantsByProduct.getOrDefault(p.getId(), List.of()).stream()
                    .filter(v -> design == null || v.getColor().getCode().equals(design.getColorCode()))
                    .sorted(Comparator.comparingInt((Inventory v) -> v.getColor().getSortOrder())
                            .thenComparingInt(v -> v.getSize().getSortOrder()))
                    .map(v -> new CartResponse.Variant(v.getSku(), v.getSize().getCode(), v.getSize().getLabel(), v.getColor().getCode(),
                            v.getColor().getName(), v.getColor().getHex(), v.getAvailable() > 0))
                    .toList();
            String image = design != null && design.getPreviewFrontKey() != null ? media(design.getPreviewFrontKey())
                    : imageFor(imagesByProduct.getOrDefault(p.getId(), List.of()), inv);
            lines.add(new CartResponse.Line(item.getId(), item.getId() != null ? "item-" + item.getId() : inv.getSku(),
                    design != null ? "CUSTOM" : "PRODUCT", p.getId(), p.getSlug(), p.getName(), image, inv.getSku(),
                    inv.getSize().getCode(), inv.getSize().getLabel(), inv.getColor().getCode(), inv.getColor().getName(),
                    inv.getColor().getHex(), item.getQuantity(), lineMax, unit, mrp, lineTotal, status, message, variants,
                    designInfo));
        }
        subtotal = money(subtotal);

        CartResponse.Coupon couponInfo = null;
        BigDecimal discount = money(BigDecimal.ZERO);
        if (couponCode != null) {
            try {
                Evaluated e = evaluate(couponCode, subtotal, userId);
                discount = e.discount();
                couponInfo = new CartResponse.Coupon(e.coupon().getCode(), e.coupon().getDescription(), true, discount, null);
            } catch (ApiException ex) {
                couponInfo = new CartResponse.Coupon(couponCode, null, false, money(BigDecimal.ZERO), ex.getMessage());
            }
        }

        BigDecimal threshold = money(settings.getDecimal(SHIPPING_FREE));
        boolean hasItems = subtotal.signum() > 0;
        BigDecimal shipping = !hasItems || subtotal.compareTo(threshold) >= 0 ? money(BigDecimal.ZERO)
                : money(settings.getDecimal(SHIPPING_FLAT));
        BigDecimal total = money(subtotal.subtract(discount).add(shipping));
        BigDecimal toFree = hasItems && subtotal.compareTo(threshold) < 0 ? money(threshold.subtract(subtotal)) : money(BigDecimal.ZERO);
        return new CartResponse("INR", lines, count, subtotal, couponInfo, discount, shipping, total, threshold, toFree, true,
                allOk && hasItems, List.copyOf(notices));
    }

    private static String imageFor(List<ProductImage> list, Inventory inv) {
        return list.stream().filter(i -> i.getColor() != null && i.getColor().getId().equals(inv.getColor().getId()))
                .findFirst()
                .or(() -> list.stream().filter(ProductImage::isPrimaryImage).findFirst())
                .or(() -> list.stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }

    private List<String> list(String raw) {
        List<String> out = new ArrayList<>();
        if (raw != null) json.readTree(raw).forEach(n -> out.add(n.stringValue()));
        return out;
    }

    /** "Front: Happy 30th (Anton)" for each text on the design. */
    private List<String> texts(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (JsonNode t : json.readTree(raw)) {
            String side = Objects.equals(t.path("side").asString(""), "BACK") ? "Back" : "Front";
            out.add(side + ": " + t.path("text").asString("") + " (" + t.path("font").asString("") + ")");
        }
        return out;
    }

    private static String media(String key) {
        return key == null ? null : "/media/" + key;
    }

    private static String variantLabel(Inventory inv) {
        return inv.getSize().getCode() + ", " + inv.getColor().getName();
    }

    private int maxQuantity() {
        return settings.getInt(MAX_QTY);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String rupees(BigDecimal value) {
        return "₹" + value.setScale(0, RoundingMode.CEILING).toPlainString();
    }
}
