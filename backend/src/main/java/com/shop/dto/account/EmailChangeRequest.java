package com.shop.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changing the sign-in email requires the current password. */
public record EmailChangeRequest(
        @NotBlank @Email @Size(max = 190) String newEmail,
        @NotBlank @Size(max = 128) String currentPassword) {
}
