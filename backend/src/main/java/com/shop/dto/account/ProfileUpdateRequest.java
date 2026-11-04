package com.shop.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Name, mobile number and WhatsApp updates. An empty phone removes it; a missing whatsappOptIn keeps the current choice. */
public record ProfileUpdateRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number") String phone,
        Boolean whatsappOptIn) {
}
