package com.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A Contact Us form submission. The admin inbox arrives in Phase 16. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contact_messages")
public class ContactMessage extends BaseEntity {

    public enum Status { NEW, READ, REPLIED, CLOSED }

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}
