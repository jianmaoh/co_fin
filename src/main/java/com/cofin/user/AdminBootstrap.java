package com.cofin.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes existing accounts listed in {@code ADMIN_USERNAMES} at startup, so admins are configured
 * per environment instead of by editing the database. Accounts registered later with one of these
 * names are promoted by {@link UserService#register}.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final AdminUsernames adminUsernames;

    public AdminBootstrap(AppUserRepository users, AdminUsernames adminUsernames) {
        this.users = users;
        this.adminUsernames = adminUsernames;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String username : adminUsernames.all()) {
            users.findByUsername(username).filter(u -> !u.isAdmin()).ifPresent(u -> {
                u.promoteToAdmin();
                log.info("Promoted '{}' to admin", username);
            });
        }
    }
}
