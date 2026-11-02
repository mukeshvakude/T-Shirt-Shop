package com.shop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.shop.config.SecurityProperties;
import com.shop.entity.Role;
import com.shop.entity.User;
import com.shop.repository.RoleRepository;
import com.shop.repository.UserRepository;
import com.shop.support.TestSecurity;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void createsAdminWithBothRolesWhenNoneExists() {
        when(userRepository.existsWithRole(Role.ADMIN)).thenReturn(false);
        when(roleRepository.findByName(Role.ADMIN)).thenReturn(Optional.of(role(Role.ADMIN)));
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(role(Role.CUSTOMER)));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        initializer(" Owner@Shop.com ", "VeryStrong123").run(null);

        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("owner@shop.com");
        assertThat(saved.getValue().hasRole(Role.ADMIN)).isTrue();
        assertThat(saved.getValue().isEmailVerified()).isTrue();
        assertThat(encoder.matches("VeryStrong123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenAnAdminExists() {
        when(userRepository.existsWithRole(Role.ADMIN)).thenReturn(true);

        initializer("owner@shop.com", "VeryStrong123").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenNotConfigured() {
        when(userRepository.existsWithRole(Role.ADMIN)).thenReturn(false);

        initializer(null, null).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesWeakAdminPassword() {
        when(userRepository.existsWithRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> initializer("owner@shop.com", "short1").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void neverPromotesAnExistingCustomerAccount() {
        when(userRepository.existsWithRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("owner@shop.com")).thenReturn(true);

        initializer("owner@shop.com", "VeryStrong123").run(null);

        verify(userRepository, never()).save(any());
    }

    private AdminAccountInitializer initializer(String email, String password) {
        SecurityProperties base = TestSecurity.properties();
        SecurityProperties props = new SecurityProperties(base.jwt(), base.refreshCookie(), base.passwordResetTtl(),
                base.login(), base.rateLimit(), new SecurityProperties.Admin(email, password, "Owner"), false);
        return new AdminAccountInitializer(props, userRepository, roleRepository, encoder);
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
