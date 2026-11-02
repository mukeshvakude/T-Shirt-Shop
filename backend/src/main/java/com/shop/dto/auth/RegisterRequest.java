package com.shop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 190) String email,
        @Pattern(regexp = PasswordRules.INDIAN_MOBILE, message = "must be a 10-digit Indian mobile number")
        String phone,
        @NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.LETTER_AND_DIGIT, message = PasswordRules.LETTER_AND_DIGIT_MESSAGE)
        String password) {
}
