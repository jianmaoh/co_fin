package com.cofin.common;

import com.cofin.user.AppUser;
import com.cofin.user.AppUserRepository;
import java.security.Principal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Maps the logged-in principal to its database row. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser get(Principal principal) {
        // The session can outlive the account (e.g. the row was deleted while logged in).
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("Account no longer exists"));
    }
}
