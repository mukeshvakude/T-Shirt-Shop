package com.shop.service;

import java.util.List;

import com.shop.dto.pricing.AdminChargeResponse;
import com.shop.dto.pricing.ChargeUpdateRequest;
import com.shop.dto.pricing.PriceEstimateRequest;
import com.shop.dto.pricing.PriceQuote;
import com.shop.dto.pricing.PricingInfo;
import com.shop.dto.pricing.ShippingSettings;
import com.shop.entity.CustomDesign;

/** Dynamic pricing for custom T-Shirts. All prices are calculated here, never trusted from clients. */
public interface PricingService {

    PricingInfo info();

    PriceQuote estimate(PriceEstimateRequest request);

    /** Current price of a saved design (one unit). */
    PriceQuote quote(CustomDesign design);

    /** Current price of a saved design for a quantity. */
    PriceQuote quote(CustomDesign design, int quantity);

    /** Current price of a saved design printed on another size (the cart allows several sizes per design). */
    PriceQuote quote(CustomDesign design, String sizeCode, int quantity);

    List<AdminChargeResponse> adminCharges();

    AdminChargeResponse saveCharge(ChargeUpdateRequest request);

    ShippingSettings shipping();

    ShippingSettings updateShipping(ShippingSettings request);
}
