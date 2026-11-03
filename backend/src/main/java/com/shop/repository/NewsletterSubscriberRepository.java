package com.shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.NewsletterSubscriber;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {

    Optional<NewsletterSubscriber> findByEmail(String email);
}
