package com.shop.service.impl;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.account.AddressRequest;
import com.shop.dto.account.AddressResponse;
import com.shop.dto.account.DashboardResponse;
import com.shop.dto.account.DashboardResponse.OrderStats;
import com.shop.dto.account.DashboardResponse.RecentOrder;
import com.shop.dto.account.EmailChangeRequest;
import com.shop.dto.account.PasswordChangeRequest;
import com.shop.dto.account.ProfileUpdateRequest;
import com.shop.dto.auth.PasswordRules;
import com.shop.dto.auth.UserResponse;
import com.shop.entity.Address;
import com.shop.entity.User;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ConflictException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.UserMapper;
import com.shop.repository.AddressRepository;
import com.shop.repository.UserRepository;
import com.shop.repository.WishlistRepository;
import com.shop.security.ClientInfo;
import com.shop.security.RateLimiter;
import com.shop.security.TokenService;
import com.shop.service.AccountService;
import com.shop.service.AuthService;
import com.shop.service.AuthService.AuthResult;
import com.shop.util.IndianStates;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);
    static final int MAX_ADDRESSES = 20;
    static final int RECENT_ORDERS = 5;
    private static final int PASSWORD_CHECKS_PER_HOUR = 10;

    private final UserRepository users;
    private final AddressRepository addressRepository;
    private final WishlistRepository wishlist;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final UserMapper userMapper;
    /** Orders arrive in Phases 12-15; until then the dashboard reads the (empty) orders table directly. */
    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ dashboard

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long userId) {
        UserResponse profile = userMapper.toResponse(findUser(userId));
        OrderStats stats = jdbc.queryForObject("""
                SELECT COUNT(*),
                       COALESCE(SUM(order_status NOT IN ('DELIVERED', 'CANCELLED')), 0),
                       COALESCE(SUM(order_status = 'DELIVERED'), 0),
                       COALESCE(SUM(order_status = 'CANCELLED'), 0)
                FROM orders WHERE user_id = ?""",
                (rs, i) -> new OrderStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), userId);
        List<RecentOrder> recent = jdbc.query("""
                SELECT o.order_number, o.placed_at, o.order_status, o.payment_status, o.grand_total,
                       COALESCE((SELECT SUM(i.quantity) FROM order_items i WHERE i.order_id = o.id), 0)
                FROM orders o WHERE o.user_id = ? ORDER BY o.placed_at DESC, o.id DESC LIMIT ?""",
                (rs, i) -> new RecentOrder(rs.getString(1), toInstant(rs.getTimestamp(2)), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getLong(6)),
                userId, RECENT_ORDERS);
        List<Address> addresses = addressRepository.findForUser(userId);
        AddressResponse defaultAddress = addresses.stream().filter(Address::isDefaultAddress).findFirst()
                .map(AccountServiceImpl::toResponse).orElse(null);
        return new DashboardResponse(profile, stats, recent, wishlist.countByUserId(userId), addresses.size(),
                defaultAddress);
    }

    // ------------------------------------------------------------------ profile, email, password

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findUser(userId);
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (phone != null && !phone.equals(user.getPhone()) && users.existsByPhone(phone)) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "This mobile number is already used by another account.");
        }
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        if (request.whatsappOptIn() != null) {
            user.setWhatsappOptIn(request.whatsappOptIn());
        }
        users.saveAndFlush(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse changeEmail(Long userId, EmailChangeRequest request, ClientInfo client) {
        User user = findUser(userId);
        requireCurrentPassword(user, request.currentPassword());
        String email = request.newEmail().trim().toLowerCase(Locale.ROOT);
        if (email.equals(user.getEmail())) {
            return userMapper.toResponse(user);
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        }
        user.setEmail(email);
        user.setEmailVerified(false);
        users.saveAndFlush(user);
        log.info("User {} changed their sign-in email", userId);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public AuthResult changePassword(Long userId, PasswordChangeRequest request, ClientInfo client) {
        User user = findUser(userId);
        requireCurrentPassword(user, request.currentPassword());
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > PasswordRules.MAX_BYTES) {
            throw new BadRequestException("PASSWORD_TOO_LONG", "Password is too long.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("PASSWORD_UNCHANGED", "Choose a password different from your current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.saveAndFlush(user);
        int revoked = tokenService.revokeAll(userId);
        log.info("User {} changed their password; revoked {} session(s)", userId, revoked);
        return authService.issueSession(userId, client);
    }

    // ------------------------------------------------------------------ addresses

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(Long userId) {
        return addressRepository.findForUser(userId).stream().map(AccountServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new BusinessRuleException("TOO_MANY_ADDRESSES",
                    "You can save up to " + MAX_ADDRESSES + " addresses. Delete one to add another.");
        }
        Address address = new Address();
        address.setUserId(userId);
        apply(address, request);
        address.setDefaultAddress(existing == 0 || request.makeDefault());
        addressRepository.saveAndFlush(address);
        if (address.isDefaultAddress()) {
            addressRepository.clearOtherDefaults(userId, address.getId());
        }
        return toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = findAddress(userId, addressId);
        apply(address, request);
        if (request.makeDefault()) {
            address.setDefaultAddress(true);
        }
        addressRepository.saveAndFlush(address);
        if (address.isDefaultAddress()) {
            addressRepository.clearOtherDefaults(userId, address.getId());
        }
        return toResponse(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = findAddress(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            // Promote the most recently updated remaining address.
            addressRepository.findForUser(userId).stream().findFirst().ifPresent(next -> {
                next.setDefaultAddress(true);
                addressRepository.save(next);
            });
        }
    }

    @Override
    @Transactional
    public AddressResponse makeDefault(Long userId, Long addressId) {
        Address address = findAddress(userId, addressId);
        address.setDefaultAddress(true);
        addressRepository.saveAndFlush(address);
        addressRepository.clearOtherDefaults(userId, addressId);
        return toResponse(address);
    }

    // ------------------------------------------------------------------ helpers

    private void requireCurrentPassword(User user, String currentPassword) {
        rateLimiter.check("password-check:" + user.getId(), PASSWORD_CHECKS_PER_HOUR, Duration.ofHours(1));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("INVALID_CURRENT_PASSWORD", "Your current password is not correct.");
        }
    }

    private static void apply(Address address, AddressRequest r) {
        String state = IndianStates.canonical(r.state());
        if (state == null) {
            throw new BadRequestException("INVALID_STATE", "Choose a state or union territory of India.");
        }
        address.setLabel(blankToNull(r.label()));
        address.setFullName(r.fullName().trim());
        address.setPhone(r.phone().trim());
        address.setLine1(r.line1().trim());
        address.setLine2(blankToNull(r.line2()));
        address.setLandmark(blankToNull(r.landmark()));
        address.setCity(r.city().trim());
        address.setState(state);
        address.setPincode(r.pincode().trim());
    }

    static AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getId(), a.getLabel(), a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(),
                a.getLandmark(), a.getCity(), a.getState(), a.getPincode(), a.getCountry(), a.isDefaultAddress());
    }

    private User findUser(Long userId) {
        return users.findWithRolesById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Address findAddress(Long userId, Long addressId) {
        // Another customer's address id behaves exactly like a missing one.
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
