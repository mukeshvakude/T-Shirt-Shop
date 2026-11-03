package com.shop.service;

import java.util.List;

import com.shop.dto.home.ContactRequest;
import com.shop.dto.home.OfferResponse;
import com.shop.dto.home.ReviewHighlight;
import com.shop.security.ClientInfo;

/** Newsletter sign-ups, contact messages, advertised offers and public review highlights. */
public interface EngagementService {

    /** Idempotent: subscribing twice, or re-subscribing after unsubscribing, simply succeeds. */
    void subscribe(String email, ClientInfo client);

    void submitContact(ContactRequest request, ClientInfo client, Long userId);

    List<OfferResponse> publicOffers();

    List<ReviewHighlight> reviewHighlights(int limit);
}
