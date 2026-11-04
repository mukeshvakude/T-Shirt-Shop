package com.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp settings ({@code app.whatsapp.*}). Everything is optional:
 * <ul>
 * <li>{@code chatNumber}: the shop's WhatsApp number for the "Chat with us" button. Empty hides the
 * button. (The admin's one-click messages are sent from whichever WhatsApp the admin is signed in to.)</li>
 * <li>{@code ownerNumber}: receives a new-order alert (Cloud API only).</li>
 * <li>{@code cloudApi}: Meta WhatsApp Cloud API credentials. Without them nothing is sent automatically.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(String chatNumber, String ownerNumber, CloudApi cloudApi) {

    public record CloudApi(String accessToken, String phoneNumberId, String apiBase, String templateLanguage) {

        public boolean configured() {
            return accessToken != null && accessToken.length() >= 20 && !"change-me".equals(accessToken)
                    && phoneNumberId != null && phoneNumberId.matches("^[0-9]{6,20}$");
        }

        public String base() {
            return apiBase == null || apiBase.isBlank() ? "https://graph.facebook.com/v21.0" : apiBase;
        }

        public String language() {
            return templateLanguage == null || templateLanguage.isBlank() ? "en" : templateLanguage;
        }
    }

    public boolean cloudConfigured() {
        return cloudApi != null && cloudApi.configured();
    }

    /** The chat number as WhatsApp wants it in links: country code and digits only, or null. */
    public String chatDigits() {
        return digits(chatNumber);
    }

    public String ownerDigits() {
        return digits(ownerNumber);
    }

    /**
     * "+91 98765 43210", "098765 43210" and "9876543210" all become "919876543210". A 10-digit Indian
     * mobile number gets the 91 country code; anything that is not a plausible number gives null.
     */
    public static String digits(String number) {
        if (number == null) {
            return null;
        }
        String d = number.replaceAll("[^0-9]", "");
        if (d.length() == 11 && d.startsWith("0")) {
            d = d.substring(1);
        }
        if (d.length() == 10 && d.matches("^[6-9].*")) {
            d = "91" + d;
        }
        return d.length() >= 11 && d.length() <= 15 ? d : null;
    }
}
