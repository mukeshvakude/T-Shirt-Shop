package com.shop.dto.home;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contact form. {@code website} is a honeypot: it is hidden from people, so any value
 * means a bot filled the form and the message is silently discarded.
 */
public record ContactRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Email @Size(max = 190) String email,
        @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number") String phone,
        @NotBlank @Size(min = 3, max = 150) String subject,
        @NotBlank @Size(min = 10, max = 5000) String message,
        @Size(max = 200) String website) {
}
