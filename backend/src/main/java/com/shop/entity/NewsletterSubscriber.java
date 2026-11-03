package com.shop.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "newsletter_subscribers")
public class NewsletterSubscriber extends BaseEntity {

    @Column(nullable = false, length = 190, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;
}
