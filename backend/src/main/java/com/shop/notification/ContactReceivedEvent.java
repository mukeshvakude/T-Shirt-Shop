package com.shop.notification;

/** A Contact us message arrived (the shop is notified when BUSINESS_EMAIL is set). */
public record ContactReceivedEvent(String name, String email, String phone, String subject, String message) {
}
