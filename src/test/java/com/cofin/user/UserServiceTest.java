package com.cofin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    AppUserRepository users;

    @Mock
    PasswordEncoder passwordEncoder;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, passwordEncoder, new AdminUsernames(" Boss, "));
    }

    @Test
    void registerStoresNormalizedUsernameAndHashedPassword() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("{bcrypt}hash");
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = service.register("  Alice ", "secret123");

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void registerPromotesConfiguredAdminUsernames() {
        when(users.existsByUsername("boss")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}hash");
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.register("BOSS", "secret123").isAdmin()).isTrue();
        when(users.existsByUsername("alice")).thenReturn(false);
        assertThat(service.register("alice", "secret123").isAdmin()).isFalse();
    }

    @Test
    void registerRejectsExistingUsernameWithoutSaving() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("ALICE", "secret123"))
                .isInstanceOf(UsernameTakenException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerTurnsUniqueConstraintRaceIntoUsernameTaken() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}hash");
        when(users.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.register("alice", "secret123"))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void loadUserByUsernameIsCaseInsensitive() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(new AppUser("alice", "{bcrypt}hash")));

        UserDetails details = service.loadUserByUsername("Alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void loadUserByUsernameThrowsForUnknownUser() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
