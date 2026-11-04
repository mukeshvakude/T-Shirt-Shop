package com.shop.dto.checkout;

import java.math.BigDecimal;

import com.shop.dto.account.AddressRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Place the order for the current cart. Use either a saved {@code addressId} or a new
 * {@code address}. {@code expectedTotal} is the total the customer saw; it is only compared with
 * the server total (a difference returns PRICE_CHANGED) and is never used as the price.
 */
public record PlaceOrderRequest(
        @NotNull @Valid Contact contact,
        Long addressId,
        @Valid AddressRequest address,
        boolean saveAddress,
        @NotBlank @Pattern(regexp = "RAZORPAY|COD") String paymentMethod,
        @Size(max = 500) String note,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal expectedTotal,
        /** Send this order's updates on WhatsApp to the contact phone (also remembered for next time). */
        boolean whatsappUpdates) {

    public record Contact(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number") String phone) {
    }
}
