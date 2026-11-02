package com.shop.security;

import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.SecurityProperties;
import com.shop.entity.Role;
import com.shop.entity.User;
import com.shop.repository.RoleRepository;
import com.shop.repository.UserRepository;

/**
 * Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD when no admin exists yet.
 * It never promotes an existing customer account, so nobody can gain admin rights
 * by registering the admin email first.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;
    private static final Pattern LETTER_AND_DIGIT = Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9]).*$");

    private final SecurityProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(SecurityProperties properties, UserRepository userRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsWithRole(Role.ADMIN)) {
            log.debug("Admin account already present");
            return;
        }
        SecurityProperties.Admin admin = properties.admin();
        if (!admin.isConfigured()) {
            log.warn("No admin account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD and restart to create one.");
            return;
        }
        String password = admin.password();
        if (password.length() < MIN_ADMIN_PASSWORD_LENGTH || !LETTER_AND_DIGIT.matcher(password).matches()) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_ADMIN_PASSWORD_LENGTH
                    + " characters and contain a letter and a digit");
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.error("ADMIN_EMAIL {} already belongs to a non-admin account. It was NOT promoted. "
                    + "Use a different ADMIN_EMAIL.", email);
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(admin.fullName() == null || admin.fullName().isBlank() ? "Store Admin" : admin.fullName());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        user.getRoles().add(roleRepository.findByName(Role.ADMIN).orElseThrow());
        user.getRoles().add(roleRepository.findByName(Role.CUSTOMER).orElseThrow());
        userRepository.save(user);
        log.info("Created admin account {} (id {})", email, user.getId());
    }
}
