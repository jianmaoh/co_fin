package com.cofin.user;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminUsernames adminUsernames;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, AdminUsernames adminUsernames) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminUsernames = adminUsernames;
    }

    /** Usernames are case-insensitive, so "Alice" and "alice" are the same account. */
    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AppUser register(String username, String rawPassword) {
        String normalized = normalize(username);
        if (users.existsByUsername(normalized)) {
            throw new UsernameTakenException(normalized);
        }
        try {
            // The unique constraint is the real guard: two concurrent requests
            // can both pass the exists check above.
            AppUser user = new AppUser(normalized, passwordEncoder.encode(rawPassword));
            if (adminUsernames.contains(normalized)) {
                user.promoteToAdmin();
            }
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameTakenException(normalized);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole())
                .build();
    }
}
