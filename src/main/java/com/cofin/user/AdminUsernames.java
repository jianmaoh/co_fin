package com.cofin.user;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Usernames from {@code ADMIN_USERNAMES} (comma-separated) that should be admins. */
@Component
public class AdminUsernames {

    private final Set<String> usernames;

    public AdminUsernames(@Value("${cofin.admin-usernames:}") String csv) {
        this.usernames = Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(UserService::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> all() {
        return usernames;
    }

    public boolean contains(String normalizedUsername) {
        return usernames.contains(normalizedUsername);
    }
}
