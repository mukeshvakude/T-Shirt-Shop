package com.shop.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.PageResponse;
import com.shop.dto.admin.AdminViews;
import com.shop.dto.admin.AdminViews.CustomerRow;
import com.shop.dto.admin.AdminViews.MessageRow;
import com.shop.dto.admin.AdminViews.OrderRow;
import com.shop.dto.admin.AdminViews.PaymentRow;
import com.shop.entity.Order;
import com.shop.entity.Payment;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.OrderRepository;
import com.shop.repository.PaymentRepository;
import com.shop.repository.RefreshTokenRepository;
import com.shop.service.OrderService;

import lombok.RequiredArgsConstructor;

/**
 * Admin portal (Phase 16): dashboard figures, order and payment lists, refunds, customers and
 * the contact inbox. Lists and figures are read with SQL; changes go through the same services
 * the shop uses (refunds through {@link PaymentProcessor}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminConsoleService {

    private static final Logger log = LoggerFactory.getLogger(AdminConsoleService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    /** Orders that count as sales: placed and not waiting for payment or cancelled. */
    private static final String SOLD = "o.order_status NOT IN ('PENDING','CANCELLED')";

    private final NamedParameterJdbcTemplate sql;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefreshTokenRepository refreshTokens;
    private final OrderService orderService;
    private final PaymentProcessor processor;
    private final Clock clock;

    // ------------------------------------------------------------------ dashboard

    public AdminViews.Dashboard dashboard() {
        Instant now = clock.instant();
        Instant today = LocalDate.now(clock.withZone(INDIA)).atStartOfDay(INDIA).toInstant();
        Instant days30 = now.minusSeconds(30L * 86400);
        MapSqlParameterSource p = new MapSqlParameterSource("since30", ts(days30)).addValue("since14",
                ts(LocalDate.now(clock.withZone(INDIA)).minusDays(13).atStartOfDay(INDIA).toInstant()));

        List<AdminViews.StatusCount> byStatus = sql.query(
                "SELECT order_status, COUNT(*) n FROM orders GROUP BY order_status ORDER BY FIELD(order_status,"
                        + "'PENDING','CONFIRMED','PROCESSING','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED')",
                (rs, i) -> new AdminViews.StatusCount(rs.getString(1), rs.getInt(2)));
        List<AdminViews.DaySales> days = sql.query("""
                SELECT DATE(CONVERT_TZ(o.placed_at, '+00:00', '+05:30')) d, COUNT(*) n, SUM(o.grand_total) s
                FROM orders o WHERE""" + " " + SOLD + """
                 AND o.placed_at >= :since14 GROUP BY d ORDER BY d""",
                p, (rs, i) -> new AdminViews.DaySales(rs.getDate(1).toLocalDate(), rs.getInt(2), money(rs.getBigDecimal(3))));
        List<AdminViews.TopProduct> top = sql.query("""
                SELECT i.product_id, MAX(i.product_name) name, SUM(i.quantity) q, SUM(i.line_total) s
                FROM order_items i JOIN orders o ON o.id = i.order_id
                WHERE""" + " " + SOLD + """
                 AND o.placed_at >= :since30 GROUP BY i.product_id ORDER BY q DESC, s DESC LIMIT 5""",
                p, (rs, i) -> new AdminViews.TopProduct(rs.getLong(1), rs.getString(2), rs.getInt(3), money(rs.getBigDecimal(4))));

        return new AdminViews.Dashboard(
                period(today), period(now.minusSeconds(7L * 86400)), period(days30),
                count("SELECT COUNT(*) FROM orders WHERE order_status IN ('CONFIRMED','PROCESSING')", Map.of()),
                count("SELECT COUNT(*) FROM orders WHERE order_status = 'PENDING' AND payment_status <> 'SUCCESS'", Map.of()),
                count("SELECT COUNT(*) FROM payments WHERE needs_review", Map.of()),
                count("""
                        SELECT COUNT(*) FROM inventory i JOIN products pr ON pr.id = i.product_id
                        WHERE i.active AND pr.status <> 'ARCHIVED' AND (i.quantity_on_hand - i.quantity_reserved) <= i.low_stock_threshold""",
                        Map.of()),
                count("SELECT COUNT(*) FROM contact_messages WHERE status = 'NEW'", Map.of()),
                count("""
                        SELECT COUNT(*) FROM users u WHERE u.created_at >= :since30 AND NOT EXISTS (
                          SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.name = 'ROLE_ADMIN')""",
                        Map.of("since30", ts(days30))),
                money(sql.queryForObject("SELECT COALESCE(SUM(refunded_amount), 0) FROM payments WHERE updated_at >= :since30",
                        p, BigDecimal.class)),
                byStatus, days, top, orderRows(null, null, false, 0, 8).content());
    }

    private AdminViews.Period period(Instant since) {
        return sql.queryForObject("SELECT COUNT(*), COALESCE(SUM(o.grand_total), 0) FROM orders o WHERE " + SOLD
                + " AND o.placed_at >= :since", Map.of("since", ts(since)), (rs, i) -> {
                    int n = rs.getInt(1);
                    BigDecimal sales = money(rs.getBigDecimal(2));
                    return new AdminViews.Period(n, sales, n == 0 ? money(BigDecimal.ZERO)
                            : sales.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
                });
    }

    // ------------------------------------------------------------------ orders

    public PageResponse<OrderRow> orderRows(String status, String q, boolean needsReview, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", blank(status) ? null : status)
                .addValue("q", blank(q) ? null : "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%")
                .addValue("review", needsReview)
                .addValue("limit", size).addValue("offset", page * size);
        String where = " " + """
                WHERE (:status IS NULL OR o.order_status = :status)
                   AND (:q IS NULL OR LOWER(o.order_number) LIKE :q OR LOWER(o.contact_email) LIKE :q OR LOWER(o.contact_name) LIKE :q
                        OR o.contact_phone LIKE :q)
                   AND (:review = FALSE OR EXISTS (SELECT 1 FROM payments pr WHERE pr.order_id = o.id AND pr.needs_review))""";
        long total = sql.queryForObject("SELECT COUNT(*) FROM orders o" + where, p, Long.class);
        List<OrderRow> rows = sql.query("""
                SELECT o.order_number, o.placed_at, o.contact_name, o.contact_email,
                       (SELECT COALESCE(SUM(i.quantity), 0) FROM order_items i WHERE i.order_id = o.id) items,
                       o.grand_total, o.order_status, o.payment_method, o.payment_status,
                       EXISTS (SELECT 1 FROM payments pr WHERE pr.order_id = o.id AND pr.needs_review) review
                FROM orders o""" + where + " ORDER BY o.placed_at DESC, o.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new OrderRow(rs.getString(1), instant(rs, 2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        money(rs.getBigDecimal(6)), rs.getString(7), rs.getString(8), rs.getString(9), rs.getBoolean(10)));
        return page(rows, page, size, total);
    }

    public AdminViews.OrderDetail orderDetail(String orderNumber) {
        Order order = orders.findByNumberWithItems(orderNumber).orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        List<PaymentRow> attempts = paymentRows("p.order_id = :orderId", Map.of("orderId", order.getId()));
        List<String> next = OrderServiceImpl.nextStatuses(order.getOrderStatus()).stream().map(Enum::name).toList();
        return new AdminViews.OrderDetail(orderService.toResponse(order), order.getUserId(), attempts, next);
    }

    // ------------------------------------------------------------------ payments

    public PageResponse<PaymentRow> paymentList(String status, boolean needsReview, String q, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", blank(status) ? null : status)
                .addValue("review", needsReview)
                .addValue("q", blank(q) ? null : "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%");
        String where = """
                (:status IS NULL OR p.status = :status) AND (:review = FALSE OR p.needs_review)
                AND (:q IS NULL OR LOWER(o.order_number) LIKE :q OR LOWER(p.razorpay_payment_id) LIKE :q OR LOWER(p.razorpay_order_id) LIKE :q)""";
        long total = sql.queryForObject("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE " + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", page * size);
        return page(paymentRows(where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset", p.getValues()),
                page, size, total);
    }

    public AdminViews.PaymentDetail paymentDetail(Long id) {
        List<PaymentRow> row = paymentRows("p.id = :id", Map.of("id", id));
        if (row.isEmpty()) throw new ResourceNotFoundException("Payment", id);
        List<AdminViews.Transaction> tx = sql.query("""
                SELECT created_at, type, status, source, gateway_reference, amount, raw_response
                FROM payment_transactions WHERE payment_id = :id ORDER BY id""", Map.of("id", id),
                (rs, i) -> new AdminViews.Transaction(instant(rs, 1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBigDecimal(6), rs.getString(7)));
        return new AdminViews.PaymentDetail(row.get(0), tx);
    }

    /** Full refund of a paid online payment (for example a return after delivery). */
    @Transactional
    public AdminViews.PaymentDetail refund(Long paymentId, String reason, Long adminId) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (!"RAZORPAY".equals(payment.getProvider()) || payment.getStatus() != Order.PaymentStatus.SUCCESS
                || payment.getRefundedAmount().signum() > 0) {
            throw new BusinessRuleException("NOT_REFUNDABLE", "Only paid online payments that were not refunded yet can be refunded here.");
        }
        Order order = orders.findByIdForUpdate(payment.getOrderId()).orElseThrow();
        processor.refund(order, payment, "Refunded by the shop: " + reason.trim(), "ADMIN", clock.instant());
        log.info("Admin {} refunded payment {} of order {}", adminId, payment.getRazorpayPaymentId(), order.getOrderNumber());
        payments.flush();
        return paymentDetail(paymentId);
    }

    @Transactional
    public AdminViews.PaymentDetail markReviewed(Long paymentId, Long adminId) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        payment.setNeedsReview(false);
        processor.log(payment, "REVIEWED", null, payment.getAmount(), "CLEARED", "ADMIN", Map.of("admin_id", adminId), clock.instant());
        payments.flush();
        return paymentDetail(paymentId);
    }

    private List<PaymentRow> paymentRows(String where, Map<String, ?> params) {
        return sql.query("""
                SELECT p.id, o.order_number, p.attempt_no, p.provider, p.status, p.razorpay_order_id, p.razorpay_payment_id, p.method,
                       p.method_detail, p.amount, p.refunded_amount, p.needs_review, p.failure_reason, p.created_at, p.paid_at
                FROM payments p JOIN orders o ON o.id = p.order_id WHERE """ + " " + where, params,
                (rs, i) -> new PaymentRow(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), money(rs.getBigDecimal(10)),
                        money(rs.getBigDecimal(11)), rs.getBoolean(12), rs.getString(13), instant(rs, 14), instant(rs, 15)));
    }

    // ------------------------------------------------------------------ customers

    private static final String NOT_ADMIN = """
            NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.name = 'ROLE_ADMIN')""";

    public PageResponse<CustomerRow> customers(String q, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("q", blank(q) ? null : "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%");
        String where = " WHERE " + NOT_ADMIN + " AND (:q IS NULL OR LOWER(u.email) LIKE :q OR LOWER(u.full_name) LIKE :q OR u.phone LIKE :q)";
        long total = sql.queryForObject("SELECT COUNT(*) FROM users u" + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", page * size);
        return page(customerRows(where + " ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset", p), page, size, total);
    }

    public AdminViews.CustomerDetail customer(Long id) {
        List<CustomerRow> row = customerRows(" WHERE u.id = :id AND " + NOT_ADMIN, new MapSqlParameterSource("id", id));
        if (row.isEmpty()) throw new ResourceNotFoundException("Customer", id);
        int addresses = count("SELECT COUNT(*) FROM addresses WHERE user_id = :id", Map.of("id", id));
        int designs = count("SELECT COUNT(*) FROM custom_designs WHERE user_id = :id", Map.of("id", id));
        List<OrderRow> recent = sql.query("""
                SELECT o.order_number, o.placed_at, o.contact_name, o.contact_email,
                       (SELECT COALESCE(SUM(i.quantity), 0) FROM order_items i WHERE i.order_id = o.id), o.grand_total,
                       o.order_status, o.payment_method, o.payment_status,
                       EXISTS (SELECT 1 FROM payments pr WHERE pr.order_id = o.id AND pr.needs_review)
                FROM orders o WHERE o.user_id = :id ORDER BY o.placed_at DESC LIMIT 20""", Map.of("id", id),
                (rs, i) -> new OrderRow(rs.getString(1), instant(rs, 2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        money(rs.getBigDecimal(6)), rs.getString(7), rs.getString(8), rs.getString(9), rs.getBoolean(10)));
        return new AdminViews.CustomerDetail(row.get(0), addresses, designs, recent);
    }

    /** Disabling signs the customer out everywhere and blocks sign-in; admins cannot be changed here. */
    @Transactional
    public AdminViews.CustomerDetail setEnabled(Long id, boolean enabled, Long adminId) {
        if (id.equals(adminId)) {
            throw new BusinessRuleException("CANNOT_CHANGE_SELF", "You cannot change your own account here.");
        }
        int changed = sql.update("UPDATE users u SET u.enabled = :enabled WHERE u.id = :id AND " + NOT_ADMIN,
                new MapSqlParameterSource("enabled", enabled).addValue("id", id));
        if (changed == 0) throw new ResourceNotFoundException("Customer", id);
        if (!enabled) {
            refreshTokens.revokeAllForUser(id, clock.instant());
        }
        log.info("Admin {} {} customer {}", adminId, enabled ? "enabled" : "disabled", id);
        return customer(id);
    }

    private List<CustomerRow> customerRows(String where, MapSqlParameterSource p) {
        return sql.query("""
                SELECT u.id, u.full_name, u.email, u.phone, u.enabled, u.created_at, u.last_login_at,
                       (SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id),
                       (SELECT COALESCE(SUM(o.grand_total), 0) FROM orders o WHERE o.user_id = u.id AND""" + " " + SOLD + """
                )
                FROM users u""" + where, p,
                (rs, i) -> new CustomerRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        instant(rs, 6), instant(rs, 7), rs.getInt(8), money(rs.getBigDecimal(9))));
    }

    // ------------------------------------------------------------------ contact inbox

    public PageResponse<MessageRow> messages(String status, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource("status", blank(status) ? null : status);
        long total = sql.queryForObject("SELECT COUNT(*) FROM contact_messages WHERE (:status IS NULL OR status = :status)", p, Long.class);
        p.addValue("limit", size).addValue("offset", page * size);
        List<MessageRow> rows = sql.query("""
                SELECT id, name, email, phone, subject, message, status, created_at FROM contact_messages
                WHERE (:status IS NULL OR status = :status) ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset""", p,
                (rs, i) -> new MessageRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), instant(rs, 8)));
        return page(rows, page, size, total);
    }

    @Transactional
    public void setMessageStatus(Long id, String status) {
        if (sql.update("UPDATE contact_messages SET status = :status WHERE id = :id", Map.of("status", status, "id", id)) == 0) {
            throw new ResourceNotFoundException("Message", id);
        }
    }

    // ------------------------------------------------------------------ helpers

    private int count(String query, Map<String, ?> params) {
        Integer n = sql.queryForObject(query, params, Integer.class);
        return n == null ? 0 : n;
    }

    private static <T> PageResponse<T> page(List<T> rows, int page, int size, long total) {
        int pages = (int) Math.ceil(total / (double) size);
        return new PageResponse<>(rows, page, size, total, pages, page == 0, page >= pages - 1);
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }

    private static Instant instant(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
