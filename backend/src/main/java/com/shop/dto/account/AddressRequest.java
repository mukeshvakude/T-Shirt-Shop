package com.shop.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 30) String label,
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number") String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 120) String landmark,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit pincode") String pincode,
        boolean makeDefault) {
}
