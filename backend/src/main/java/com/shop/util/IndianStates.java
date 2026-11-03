package com.shop.util;

import java.util.List;

/** States and union territories of India, as shown in address forms. */
public final class IndianStates {

    public static final List<String> ALL = List.of(
            "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chandigarh",
            "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa", "Gujarat", "Haryana",
            "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Ladakh", "Lakshadweep",
            "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry",
            "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand",
            "West Bengal");

    private IndianStates() {
    }

    /** The canonical spelling for a state name (case-insensitive), or null when unknown. */
    public static String canonical(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return ALL.stream().filter(s -> s.equalsIgnoreCase(trimmed)).findFirst().orElse(null);
    }
}
