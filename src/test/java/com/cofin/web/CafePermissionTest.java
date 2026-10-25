package com.cofin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cofin.cafe.CafeRepository;
import com.cofin.cafe.CafeService;
import com.cofin.user.AdminBootstrap;
import com.cofin.user.AppUser;
import com.cofin.user.AppUserRepository;
import com.cofin.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:permissions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "cofin.admin-usernames=boss, Late_Boss",
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CafePermissionTest {

    static final RequestPostProcessor ALICE = user("alice");
    static final RequestPostProcessor BOB = user("bob");
    static final RequestPostProcessor BOSS = user("boss").roles("ADMIN");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService userService;

    @Autowired
    AppUserRepository users;

    @Autowired
    CafeService cafeService;

    @Autowired
    CafeRepository cafes;

    @Autowired
    AdminBootstrap adminBootstrap;

    AppUser alice;
    AppUser boss;
    Long cafeId;

    @BeforeEach
    void setUp() {
        for (String table : new String[] {"review_photo", "review", "collection_item", "cafe_collection",
                "cafe_tag", "tag", "cafe", "app_user"}) {
            jdbc.update("delete from " + table);
        }
        alice = userService.register("alice", "secret123");
        userService.register("bob", "secret123");
        boss = userService.register("boss", "secret123");
        cafeId = cafeService.create("TTT", "測試地址", alice.getId()).id();
    }

    @Test
    void configuredUsernamesBecomeAdmins() {
        assertThat(boss.isAdmin()).isTrue();
        assertThat(alice.isAdmin()).isFalse();

        // An account that existed before being listed is promoted at startup.
        jdbc.update("insert into app_user (username, password_hash, role) values ('late_boss', '{noop}x', 'USER')");
        adminBootstrap.run(null);
        assertThat(users.findByUsername("late_boss").orElseThrow().isAdmin()).isTrue();
    }

    @Test
    void creatorCanDeleteTheirPendingCafe() throws Exception {
        mvc.perform(get("/cafes/" + cafeId).with(ALICE))
                .andExpect(content().string(containsString("待審核")))
                .andExpect(content().string(containsString("刪除咖啡廳")));

        mvc.perform(post("/cafes/" + cafeId + "/delete").with(ALICE).with(csrf()))
                .andExpect(redirectedUrl("/cafes"));
        assertThat(cafes.findById(cafeId)).isEmpty();
    }

    @Test
    void otherUsersCannotDeleteSomeoneElsesCafe() throws Exception {
        mvc.perform(get("/cafes/" + cafeId).with(BOB))
                .andExpect(content().string(not(containsString("刪除咖啡廳"))));

        mvc.perform(post("/cafes/" + cafeId + "/delete").with(BOB).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(cafes.findById(cafeId)).isPresent();
    }

    @Test
    void afterApprovalOnlyAdminsCanDelete() throws Exception {
        mvc.perform(post("/admin/cafes/" + cafeId + "/approve").with(BOSS).with(csrf()))
                .andExpect(redirectedUrl("/admin/cafes"));
        assertThat(cafes.findById(cafeId).orElseThrow().isApproved()).isTrue();

        mvc.perform(get("/cafes/" + cafeId).with(ALICE))
                .andExpect(content().string(not(containsString("待審核"))))
                .andExpect(content().string(not(containsString("刪除咖啡廳"))));
        mvc.perform(post("/cafes/" + cafeId + "/delete").with(ALICE).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(cafes.findById(cafeId)).isPresent();

        mvc.perform(post("/cafes/" + cafeId + "/delete").with(BOSS).with(csrf()))
                .andExpect(redirectedUrl("/cafes"));
        assertThat(cafes.findById(cafeId)).isEmpty();
    }

    @Test
    void onlyAdminsCanApproveOrOpenTheAdminPage() throws Exception {
        mvc.perform(get("/admin/cafes").with(ALICE)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/cafes/" + cafeId + "/approve").with(ALICE).with(csrf()))
                .andExpect(status().isForbidden());
        // The cafe-page approve route is not under /admin, so the service check is what stops it.
        mvc.perform(post("/cafes/" + cafeId + "/approve").with(ALICE).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(cafes.findById(cafeId).orElseThrow().isApproved()).isFalse();

        mvc.perform(get("/admin/cafes").with(BOSS))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TTT")))
                .andExpect(content().string(containsString("新增者：alice")));
        mvc.perform(get("/cafes/" + cafeId).with(BOSS))
                .andExpect(content().string(containsString("核准這家店")));
    }

    @Test
    void adminSeesReviewLinkInNavOthersDoNot() throws Exception {
        mvc.perform(get("/cafes").with(BOSS)).andExpect(content().string(containsString("/admin/cafes")));
        mvc.perform(get("/cafes").with(ALICE)).andExpect(content().string(not(containsString("/admin/cafes"))));
    }

    @Test
    void deletingCafeCascadesItsReviewsAndTags() throws Exception {
        cafeService.addTag(cafeId, alice.getId(), "安靜");
        jdbc.update("insert into review (cafe_id, user_id, rating) values (?, ?, 4)", cafeId, alice.getId());

        mvc.perform(post("/cafes/" + cafeId + "/delete").with(ALICE).with(csrf()));

        assertThat(jdbc.queryForObject("select count(*) from review", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cafe_tag", Long.class)).isZero();
    }
}
