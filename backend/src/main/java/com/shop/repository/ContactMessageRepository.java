package com.shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
}
