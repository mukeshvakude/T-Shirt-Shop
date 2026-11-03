package com.shop.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.AppProperties;
import com.shop.dto.account.AddressRequest;
import com.shop.dto.account.AddressResponse;
import com.shop.dto.cart.CartResponse;
import com.shop.dto.checkout.CheckoutResponse;
import com.shop.dto.checkout.PlaceOrderRequest;
import com.shop.dto.order.OrderResponse;
import com.shop.entity.Cart;
import com.shop.entity.Coupon;
import com.shop.entity.CouponRedemption;
import com.shop.entity.CustomDesign;
import com.shop.entity.Inventory;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.OrderStatusHistory;
import com.shop.entity.User;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ConflictException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.CartRepository;
import com.shop.repository.CouponRedemptionRepository;
import com.shop.repository.CouponRepository;
import com.shop.repository.CustomDesignRepository;
import com.shop.repository.InventoryRepository;
import com.shop.repository.OrderRepository;
import com.shop.repository.OrderStatusHistoryRepository;
import com.shop.repository.UserRepository;
import com.shop.service.AccountService;
import com.shop.service.AppSettingService;
import com.shop.service.CartService;
import com.shop.service.CheckoutService;
import com.shop.service.OrderService;
import com.shop.util.IndianStates;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checkout: re-prices the locked cart on the server, checks the total the customer saw, holds the
 * stock, snapshots prices, address and contact into a new order, records the coupon use, locks the
 * custom designs and empties the cart, all in one transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutServiceImpl.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CartService cartService;
    private final CartRepository carts;
    private final AccountService accounts;
    private final UserRepository users;
    private final InventoryRepository inventory;
    private final CustomDesignRepository designs;
    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final StockService stock;
    private final OrderService orderService;
    private final AppSettingService settings;
    private final AppProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    // ------------------------------------------------------------------ summary

    @Override
    @Transactional(readOnly = true)
    public CheckoutResponse summary(Long userId) {
        CartResponse cart = cartService.get(userId);
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        CheckoutResponse.Totals totals = totals(cart);
        return new CheckoutResponse(cart, totals,
                new CheckoutResponse.Contact(user.getFullName(), user.getEmail(), user.getPhone()),
                accounts.addresses(userId), paymentOptions(totals.total()), settings.getInt("stock.reservation_minutes"));
    }

    // ------------------------------------------------------------------ place order

    @Override
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest r) {
        Instant now = clock.instant();
        Cart cartRow = carts.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessRuleException("CART_EMPTY", "Your bag is empty."));
        CartResponse cart = cartService.get(userId);
        if (cart.items().isEmpty()) {
            throw new BusinessRuleException("CART_EMPTY", "Your bag is empty.");
        }
        if (!cart.canCheckout()) {
            String reason = cart.items().stream().filter(i -> !buyable(i)).map(i -> i.name() + ": " + i.message())
                    .findFirst().orElse("Some items in your bag need your attention.");
            throw new ConflictException("CART_NOT_READY", reason);
        }
        CheckoutResponse.Totals totals = totals(cart);
        if (r.expectedTotal().setScale(2, RoundingMode.HALF_UP).compareTo(totals.total()) != 0) {
            throw new ConflictException("PRICE_CHANGED",
                    "Your order total is now " + rupees(totals.total()) + ". Please check the summary and place the order again.");
        }

        Order.PaymentMethod method = Order.PaymentMethod.valueOf(r.paymentMethod());
        CheckoutResponse.PaymentOption option = paymentOptions(totals.total()).stream()
                .filter(o -> o.method().equals(method.name())).findFirst().orElseThrow();
        if (!option.available()) {
            throw new BusinessRuleException(method == Order.PaymentMethod.COD ? "COD_UNAVAILABLE" : "ONLINE_PAYMENT_UNAVAILABLE",
                    option.message());
        }

        Map<String, Object> address = address(userId, r);
        Coupon coupon = lockCoupon(cart, userId);

        Order order = new Order();
        order.setOrderNumber(newOrderNumber(now));
        order.setUserId(userId);
        order.setPaymentMethod(method);
        order.setSubtotal(totals.itemsTotal());
        order.setCustomizationTotal(totals.customizationTotal());
        order.setShippingCharge(totals.shipping());
        order.setDiscountTotal(totals.discount());
        order.setGrandTotal(totals.total());
        order.setContactName(r.contact().name().trim());
        order.setContactEmail(r.contact().email().trim().toLowerCase(java.util.Locale.ROOT));
        order.setContactPhone(r.contact().phone().trim());
        order.setShippingAddress(json.writeValueAsString(address));
        order.setCustomerNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        order.setPlacedAt(now);
        if (coupon != null) {
            order.setCouponId(coupon.getId());
            order.setCouponCode(coupon.getCode());
        }
        boolean cod = method == Order.PaymentMethod.COD;
        Instant holdUntil = now.plus(Duration.ofMinutes(settings.getInt("stock.reservation_minutes")));
        if (cod) {
            order.setOrderStatus(Order.Status.CONFIRMED);
            order.setPaymentStatus(Order.PaymentStatus.PENDING);
            order.setConfirmedAt(now);
        } else {
            order.setOrderStatus(Order.Status.PENDING);
            order.setPaymentStatus(Order.PaymentStatus.CREATED);
            order.setReservationExpiresAt(holdUntil);
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CartResponse.Line line : cart.items()) {
            Inventory inv = inventory.findBySkuWithDetails(line.sku())
                    .orElseThrow(() -> new ConflictException("CART_NOT_READY", line.name() + " is no longer available."));
            CustomDesign design = line.design() == null ? null : designs.findByCode(line.design().code())
                    .filter(d -> d.getUserId().equals(userId))
                    .orElseThrow(() -> new ConflictException("CART_NOT_READY", "A design in your bag was deleted."));
            order.getItems().add(item(order, line, inv, design));
            quantities.merge(inv.getId(), line.quantity(), Integer::sum);
            if (design != null) {
                design.setLocked(true);
                design.setStatus(CustomDesign.Status.ORDERED);
            }
        }
        orders.saveAndFlush(order);

        stock.hold(order.getId(), quantities, holdUntil, cod, now);

        if (coupon != null) {
            CouponRedemption redemption = new CouponRedemption();
            redemption.setCouponId(coupon.getId());
            redemption.setUserId(userId);
            redemption.setOrderId(order.getId());
            redemption.setDiscountAmount(totals.discount());
            redemption.setCreatedAt(now);
            redemptions.save(redemption);
            coupon.setUsedCount(coupon.getUsedCount() + 1);
        }

        record(order, null, order.getOrderStatus().name(), userId, now, cod
                ? "Order placed with Cash on Delivery"
                : "Order placed; waiting for online payment (stock held until payment window ends)");

        cartRow.getItems().clear();
        cartRow.setCouponCode(null);
        carts.saveAndFlush(cartRow);
        log.info("Order {} placed by user {}: {} {} via {}", order.getOrderNumber(), userId, order.getCurrency(),
                order.getGrandTotal(), method);
        return orderService.toResponse(order);
    }

    // ------------------------------------------------------------------ helpers

    /** Splits the cart total the way orders store it: base prices, customization charges, shipping, discount. */
    private static CheckoutResponse.Totals totals(CartResponse cart) {
        BigDecimal items = BigDecimal.ZERO;
        for (CartResponse.Line line : cart.items()) {
            if (!buyable(line)) continue;
            BigDecimal base = line.design() != null ? line.design().basePrice() : line.unitPrice();
            items = items.add(base.multiply(BigDecimal.valueOf(line.quantity())));
        }
        items = items.setScale(2, RoundingMode.HALF_UP);
        BigDecimal custom = cart.subtotal().subtract(items).setScale(2, RoundingMode.HALF_UP);
        return new CheckoutResponse.Totals(items, custom, cart.shipping(), cart.discount(), cart.total());
    }

    private List<CheckoutResponse.PaymentOption> paymentOptions(BigDecimal total) {
        AppProperties.Payments payments = properties.payments();
        boolean online = payments != null && payments.razorpayConfigured();
        boolean codEnabled = settings.getBoolean("cod.enabled");
        BigDecimal codMax = settings.getDecimal("cod.max_order_value");
        String codMessage = !codEnabled ? "Cash on Delivery is not available right now."
                : total.compareTo(codMax) > 0 ? "Cash on Delivery is available for orders up to " + rupees(codMax) + "." : null;
        return List.of(
                new CheckoutResponse.PaymentOption("RAZORPAY", online,
                        online ? null : "Online payment is not available yet. Please choose Cash on Delivery."),
                new CheckoutResponse.PaymentOption("COD", codMessage == null, codMessage));
    }

    /** Address snapshot from a saved address or a new one (saved to the address book when asked). */
    private Map<String, Object> address(Long userId, PlaceOrderRequest r) {
        if ((r.addressId() == null) == (r.address() == null)) {
            throw new BadRequestException("ADDRESS_REQUIRED", "Choose a saved address or enter a new one.");
        }
        AddressResponse a;
        if (r.addressId() != null) {
            a = accounts.addresses(userId).stream().filter(x -> x.id().equals(r.addressId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Address", r.addressId()));
        } else {
            AddressRequest req = r.address();
            String state = IndianStates.canonical(req.state());
            if (state == null) {
                throw new BadRequestException("INVALID_STATE", "Choose a state or union territory of India.");
            }
            a = r.saveAddress() ? accounts.createAddress(userId, req)
                    : new AddressResponse(null, req.label(), req.fullName().trim(), req.phone().trim(), req.line1().trim(),
                            blank(req.line2()), blank(req.landmark()), req.city().trim(), state, req.pincode().trim(), "India", false);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", a.fullName());
        m.put("phone", a.phone());
        m.put("line1", a.line1());
        m.put("line2", a.line2());
        m.put("landmark", a.landmark());
        m.put("city", a.city());
        m.put("state", a.state());
        m.put("pincode", a.pincode());
        m.put("country", "India");
        return m;
    }

    /** Re-checks the coupon under a row lock so the last use of a limited coupon cannot be taken twice. */
    private Coupon lockCoupon(CartResponse cart, Long userId) {
        if (cart.coupon() == null || !cart.coupon().applied() || cart.discount().signum() == 0) {
            return null;
        }
        Coupon found = coupons.findByCode(cart.coupon().code()).orElse(null);
        Coupon c = found == null ? null : coupons.findByIdForUpdate(found.getId()).orElse(null);
        boolean usable = c != null && c.isActive()
                && (c.getUsageLimit() == null || c.getUsedCount() < c.getUsageLimit())
                && coupons.countRedemptions(c.getId(), userId) < c.getPerUserLimit();
        if (!usable) {
            throw new ConflictException("PRICE_CHANGED",
                    "The coupon " + cart.coupon().code() + " can no longer be used. Please check your bag and try again.");
        }
        return c;
    }

    private OrderItem item(Order order, CartResponse.Line line, Inventory inv, CustomDesign design) {
        BigDecimal base = design != null ? line.design().basePrice() : line.unitPrice();
        BigDecimal custom = line.unitPrice().subtract(base);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(line.productId());
        item.setInventoryId(inv.getId());
        item.setCustomDesign(design);
        item.setProductName(line.name());
        item.setSku(line.sku());
        item.setSizeCode(line.sizeCode());
        item.setColorCode(line.colorCode());
        item.setImageUrl(line.imageUrl());
        item.setQuantity(line.quantity());
        item.setUnitBasePrice(base);
        item.setUnitCustomizationCharge(custom);
        item.setLineTotal(base.add(custom).multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP));
        ObjectNode breakdown = json.createObjectNode();
        breakdown.put("mrp", line.unitMrp());
        if (line.design() != null) {
            breakdown.put("designCode", line.design().code());
            breakdown.putPOJO("placements", line.design().placements());
            breakdown.put("premiumPrint", line.design().premiumPrint());
            ArrayNode charges = breakdown.putArray("charges");
            line.design().charges().forEach(c -> charges.addObject()
                    .put("type", c.type()).put("code", c.code()).put("label", c.label()).put("amount", c.amount()));
        }
        item.setPriceBreakdown(breakdown.toString());
        return item;
    }

    private void record(Order order, String from, String to, Long by, Instant at, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(by);
        h.setNote(note);
        h.setCreatedAt(at);
        history.save(h);
    }

    /** TS-yyyyMMdd-NNNNNN (date in India), random and unique. */
    private String newOrderNumber(Instant now) {
        String day = DAY.format(now.atZone(INDIA));
        for (int attempt = 0; attempt < 20; attempt++) {
            String number = "TS-" + day + "-" + String.format("%06d", RANDOM.nextInt(1_000_000));
            if (!orders.existsByOrderNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Could not generate a unique order number");
    }

    private static boolean buyable(CartResponse.Line line) {
        return "OK".equals(line.status()) || "LOW_STOCK".equals(line.status());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String rupees(BigDecimal value) {
        return "₹" + value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
