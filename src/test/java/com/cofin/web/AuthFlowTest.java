package com.cofin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cofin.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Feature test: the full app on in-memory H2, driven through HTTP like a browser would. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:authflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll();
    }

    @Test
    void registerThenLoginWithDifferentCase() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "Alice")
                        .param("password", "secret123")
                        .param("confirmPassword", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(users.findByUsername("alice")).hasValueSatisfying(user ->
                assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("secret123"));

        mvc.perform(formLogin("/login").user("ALICE").password("secret123"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("alice"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        register("bob", "secret123");

        mvc.perform(formLogin("/login").user("bob").password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void duplicateUsernameShowsFormError() throws Exception {
        register("carol", "secret123");

        mvc.perform(post("/register").with(csrf())
                        .param("username", "CAROL")
                        .param("password", "another123")
                        .param("confirmPassword", "another123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("form", "username", "taken"));
    }

    @Test
    void mismatchedAndInvalidInputIsRejected() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "x!")
                        .param("password", "short")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "username", "password", "confirmPassword"));

        assertThat(users.count()).isZero();
    }

    @Test
    void registerWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/register")
                        .param("username", "mallory")
                        .param("password", "secret123")
                        .param("confirmPassword", "secret123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void homePageShowsLoginLinksToAnonymousUsers() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/register")));
    }

    @Test
    void protectedPageRedirectsToLogin() throws Exception {
        mvc.perform(get("/some-protected-page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private void register(String username, String password) throws Exception {
        mvc.perform(post("/register").with(csrf())
                .param("username", username)
                .param("password", password)
                .param("confirmPassword", password));
    }
}
