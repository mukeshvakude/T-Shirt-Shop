package com.shop.notification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.WhatsAppProperties;
import com.shop.dto.order.OrderResponse;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.OrderRepository;
import com.shop.service.OrderService;

import lombok.RequiredArgsConstructor;

/**
 * One-click WhatsApp messages for the admin order page: wa.me links that open WhatsApp (app or
 * web) with the update already typed to the customer. Nothing is sent by the server; the admin
 * presses send. Needs no API account.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppLinks {

    public record Link(String type, String label, String text, String url) {
    }

    /**
     * @param phone    customer number with country code, or null when it is not a valid mobile number
     * @param optedIn  the customer asked for this order's updates on WhatsApp
     * @param automatic the Cloud API sends updates automatically (so manual ones are rarely needed)
     */
    public record OrderLinks(String phone, boolean optedIn, boolean automatic, List<Link> messages) {
    }

    private final OrderRepository orders;
    private final OrderService orderService;
    private final WhatsAppMessages messages;
    private final WhatsAppProperties properties;

    @Transactional(readOnly = true)
    public OrderLinks forOrder(String orderNumber) {
        OrderResponse o = orders.findByNumberWithItems(orderNumber).map(orderService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        String phone = WhatsAppProperties.digits(o.contact().phone());
        List<Link> links = phone == null ? List.of()
                : messages.forAdmin(o).stream().map(m -> new Link(m.type(), m.label(), m.text(), url(phone, m.text()))).toList();
        return new OrderLinks(phone, o.contact().whatsappUpdates(), properties.cloudConfigured(), links);
    }

    static String url(String phone, String text) {
        return "https://wa.me/" + phone + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
