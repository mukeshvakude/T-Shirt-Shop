package com.shop.service;

import java.util.List;

import com.shop.dto.account.AddressRequest;
import com.shop.dto.account.AddressResponse;
import com.shop.dto.account.DashboardResponse;
import com.shop.dto.account.EmailChangeRequest;
import com.shop.dto.account.PasswordChangeRequest;
import com.shop.dto.account.ProfileUpdateRequest;
import com.shop.dto.auth.UserResponse;
import com.shop.security.ClientInfo;
import com.shop.service.AuthService.AuthResult;

/** The signed-in customer's own profile, password, addresses and dashboard. */
public interface AccountService {

    DashboardResponse dashboard(Long userId);

    UserResponse updateProfile(Long userId, ProfileUpdateRequest request);

    UserResponse changeEmail(Long userId, EmailChangeRequest request, ClientInfo client);

    /** Changes the password, signs out every other session and returns a new session for this device. */
    AuthResult changePassword(Long userId, PasswordChangeRequest request, ClientInfo client);

    List<AddressResponse> addresses(Long userId);

    AddressResponse createAddress(Long userId, AddressRequest request);

    AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request);

    void deleteAddress(Long userId, Long addressId);

    AddressResponse makeDefault(Long userId, Long addressId);
}
