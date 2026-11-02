package com.shop.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.LETTER_AND_DIGIT, message = PasswordRules.LETTER_AND_DIGIT_MESSAGE)
        String newPassword) {
}
