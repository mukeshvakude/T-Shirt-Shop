package com.shop.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.account.AddressRequest;
import com.shop.dto.account.AddressResponse;
import com.shop.dto.account.DashboardResponse;
import com.shop.dto.account.EmailChangeRequest;
import com.shop.dto.account.PasswordChangeRequest;
import com.shop.dto.account.ProfileUpdateRequest;
import com.shop.dto.auth.AuthResponse;
import com.shop.dto.auth.UserResponse;
import com.shop.security.ClientInfo;
import com.shop.security.RefreshCookies;
import com.shop.service.AccountService;
import com.shop.service.AuthService;
import com.shop.util.IndianStates;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Customer account", description = "Dashboard, profile, password and addresses of the signed-in customer")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;
    private final RefreshCookies cookies;

    @GetMapping("/dashboard")
    @Operation(summary = "Profile, order counts, recent orders, wishlist and address summary")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return accountService.dashboard(userId(jwt));
    }

    @GetMapping("/profile")
    @Operation(summary = "The signed-in customer")
    public UserResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return authService.getUser(userId(jwt));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update name and mobile number")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(userId(jwt), request);
    }

    @PutMapping("/email")
    @Operation(summary = "Change the sign-in email (requires the current password)")
    public UserResponse changeEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EmailChangeRequest request,
            HttpServletRequest http) {
        return accountService.changeEmail(userId(jwt), request, ClientInfo.from(http));
    }

    @PutMapping("/password")
    @Operation(summary = "Change password; signs out other devices and renews this session")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request, HttpServletRequest http) {
        return AuthController.withSession(HttpStatus.OK,
                accountService.changePassword(userId(jwt), request, ClientInfo.from(http)), cookies);
    }

    @GetMapping("/addresses")
    @Operation(summary = "Saved addresses, default first")
    public List<AddressResponse> addresses(@AuthenticationPrincipal Jwt jwt) {
        return accountService.addresses(userId(jwt));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Add an address (the first one becomes the default)")
    public ResponseEntity<AddressResponse> createAddress(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAddress(userId(jwt), request));
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Edit an address")
    public AddressResponse updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return accountService.updateAddress(userId(jwt), id, request);
    }

    @PutMapping("/addresses/{id}/default")
    @Operation(summary = "Make an address the default")
    public AddressResponse makeDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.makeDefault(userId(jwt), id);
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "Delete an address (another one becomes the default if needed)")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        accountService.deleteAddress(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/states")
    @Operation(summary = "States and union territories for address forms")
    public List<String> states() {
        return IndianStates.ALL;
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
