package com.shop.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.SecurityProperties;
import com.shop.dto.home.ContactRequest;
import com.shop.dto.home.OfferResponse;
import com.shop.dto.home.ReviewHighlight;
import com.shop.entity.ContactMessage;
import com.shop.entity.Coupon;
import com.shop.entity.NewsletterSubscriber;
import com.shop.entity.ProductStatus;
import com.shop.entity.Review;
import com.shop.repository.ContactMessageRepository;
import com.shop.repository.CouponRepository;
import com.shop.repository.NewsletterSubscriberRepository;
import com.shop.repository.ReviewRepository;
import com.shop.security.ClientInfo;
import com.shop.security.RateLimiter;
import com.shop.service.EngagementService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EngagementServiceImpl implements EngagementService {

    private static final Logger log = LoggerFactory.getLogger(EngagementServiceImpl.class);
    static final int MIN_HIGHLIGHT_RATING = 4;

    private final NewsletterSubscriberRepository subscribers;
    private final ContactMessageRepository contactMessages;
    private final CouponRepository coupons;
    private final ReviewRepository reviews;
    private final RateLimiter rateLimiter;
    private final SecurityProperties security;
    private final Clock clock;

    @Override
    @Transactional
    public void subscribe(String email, ClientInfo client) {
        rateLimiter.check("newsletter:" + client.ipAddress(), security.rateLimit().newsletterPerHour(), Duration.ofHours(1));
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        NewsletterSubscriber subscriber = subscribers.findByEmail(normalized).orElse(null);
        if (subscriber == null) {
            subscriber = new NewsletterSubscriber();
            subscriber.setEmail(normalized);
            try {
                subscribers.saveAndFlush(subscriber);
            } catch (DataIntegrityViolationException race) {
                // Another request subscribed the same address at the same moment: already done.
                return;
            }
            log.info("New newsletter subscriber {}", subscriber.getId());
            return;
        }
        if (!subscriber.isActive()) {
            subscriber.setActive(true);
            subscriber.setUnsubscribedAt(null);
        }
    }

    @Override
    @Transactional
    public void submitContact(ContactRequest request, ClientInfo client, Long userId) {
        rateLimiter.check("contact:" + client.ipAddress(), security.rateLimit().contactPerHour(), Duration.ofHours(1));
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Discarded contact form submission caught by the honeypot");
            return;
        }
        ContactMessage message = new ContactMessage();
        message.setUserId(userId);
        message.setName(request.name().trim());
        message.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        message.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        message.setSubject(request.subject().trim());
        message.setMessage(request.message().trim());
        message.setIpAddress(client.ipAddress());
        message.setUserAgent(client.userAgent());
        contactMessages.save(message);
        log.info("Contact message {} received", message.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfferResponse> publicOffers() {
        return coupons.findPublicActive(clock.instant()).stream().map(EngagementServiceImpl::toOffer).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewHighlight> reviewHighlights(int limit) {
        return reviews.findHighlights(MIN_HIGHLIGHT_RATING, ProductStatus.ACTIVE, PageRequest.of(0, limit)).stream()
                .map(EngagementServiceImpl::toHighlight)
                .toList();
    }

    static OfferResponse toOffer(Coupon c) {
        String headline = c.getDiscountType() == Coupon.DiscountType.PERCENT
                ? plain(c.getDiscountValue()) + "% off"
                : "₹" + plain(c.getDiscountValue()) + " off";
        return new OfferResponse(c.getCode(), headline, c.getDescription(), c.getDiscountType().name(),
                c.getDiscountValue(), c.getMinOrderAmount(), c.getMaxDiscount(), c.getValidTo());
    }

    static ReviewHighlight toHighlight(Review r) {
        return new ReviewHighlight(r.getId(), publicName(r.getUser().getFullName()), r.getRating(), r.getTitle(),
                r.getComment(), r.getProduct().getName(), r.getProduct().getSlug(), r.getCreatedAt());
    }

    /** "Ananya Sharma" becomes "Ananya S."; a single name is shown as is. */
    static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
