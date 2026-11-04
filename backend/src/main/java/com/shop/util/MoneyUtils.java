package com.shop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. Amounts are INR with 2 decimals in the app and paise (long)
 * only at the Razorpay boundary.
 */
public final class MoneyUtils {

    public static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtils() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** INR to paise, e.g. 429.00 to 42900. Rejects fractions of a paisa. */
    public static long toPaise(BigDecimal rupees) {
        if (rupees == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (rupees.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        try {
            return rupees.multiply(HUNDRED).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amount has more than 2 decimals: " + rupees, ex);
        }
    }

    /** Paise to INR, e.g. 42900 to 429.00. */
    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).divide(HUNDRED, SCALE, RoundingMode.UNNECESSARY);
    }

    /** Percentage of an amount, rounded half-up to 2 decimals. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return normalize(amount.multiply(percent).divide(HUNDRED, SCALE + 2, RoundingMode.HALF_UP));
    }

    /**
     * Indian digit grouping without a currency sign: 125000 to "1,25,000", 1047.5 to "1,047.50".
     * {@code alwaysPaise} prints ".00" for whole amounts too (invoices); otherwise whole rupees have no decimals.
     */
    public static String groupIndian(BigDecimal amount, boolean alwaysPaise) {
        BigDecimal value = amount.abs().setScale(SCALE, RoundingMode.HALF_UP);
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        String plain = value.toPlainString();
        String integer = plain.substring(0, plain.indexOf('.'));
        String fraction = plain.substring(plain.indexOf('.'));
        StringBuilder out = new StringBuilder();
        int n = integer.length();
        if (n <= 3) {
            out.append(integer);
        } else {
            String head = integer.substring(0, n - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) out.append(',');
                out.append(head.charAt(i));
            }
            out.append(',').append(integer, n - 3, n);
        }
        if (alwaysPaise || !whole) out.append(fraction);
        return (amount.signum() < 0 ? "-" : "") + out;
    }
}
