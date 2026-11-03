package com.shop.dto.account;

import com.shop.dto.auth.PasswordRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.LETTER_AND_DIGIT, message = PasswordRules.LETTER_AND_DIGIT_MESSAGE)
        String newPassword) {
}
