package com.cofin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cofin.cafe.CafeService;
import com.cofin.collection.CollectionService;
import com.cofin.recommend.RecommendationService;
import com.cofin.user.AppUser;
import com.cofin.user.UserService;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:home;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HomeAndRecommendationTest {

    static final String PLACE_A = "ChIJAAAAAAAAAAAAAAAAAAAAAAA";
    static final String PLACE_B = "ChIJBBBBBBBBBBBBBBBBBBBBBBB";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService userService;

    @Autowired
    CafeService cafeService;

    @Autowired
    CollectionService collectionService;

    @Autowired
    RecommendationService recommendationService;

    AppUser alice;
    AppUser bob;

    @BeforeEach
    void setUp() {
        for (String table : new String[] {"collection_item", "cafe_collection", "cafe_tag", "tag", "cafe", "app_user"}) {
            jdbc.update("delete from " + table);
        }
        alice = userService.register("alice", "secret123");
        bob = userService.register("bob", "secret123");
    }

    // ---------- popular ranking ----------

    @Test
    void popularRanksBySavesThenTagVotesAndSkipsInactiveCafes() {
        Long saved = cafeService.create("兩人收藏", null, alice.getId()).id();
        Long tagged = cafeService.create("很多標籤", null, alice.getId()).id();
        cafeService.create("沒人理", null, alice.getId());

        Long aliceList = collectionService.create(alice.getId(), "清單");
        Long bobList = collectionService.create(bob.getId(), "清單");
        collectionService.addItem(alice.getId(), aliceList, saved, null);
        collectionService.addItem(bob.getId(), bobList, saved, null);
        cafeService.addTag(saved, alice.getId(), "安靜");
        for (String tag : new String[] {"安靜", "有插座", "不限時"}) {
            cafeService.addTag(tagged, alice.getId(), tag);
            cafeService.addTag(tagged, bob.getId(), tag);
        }

        assertThat(recommendationService.popular())
                .extracting("name", "saves", "votes")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("兩人收藏", 2L, 1L),
                        org.assertj.core.groups.Tuple.tuple("很多標籤", 0L, 6L));
        assertThat(recommendationService.popular().get(1).tags()).extracting("name")
                .containsExactly("不限時", "安靜", "有插座");
    }

    // ---------- home page ----------

    @Test
    void anonymousVisitorsSeeTheLandingPage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/register")))
                .andExpect(content().string(not(containsString("熱門咖啡廳"))));
    }

    @Test
    void homeShowsPopularCafesAndExplainsMissingMapsKey() throws Exception {
        Long id = cafeService.create("貓下去", null, alice.getId()).id();
        cafeService.addTag(id, alice.getId(), "安靜");

        mvc.perform(get("/").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Co-Fin 熱門咖啡廳")))
                .andExpect(content().string(containsString("貓下去")))
                .andExpect(content().string(containsString("需要設定 Google Maps 金鑰")))
                .andExpect(content().string(not(containsString("nearby-button"))));
    }

    @Test
    void homeWithNoActivityShowsEmptyState() throws Exception {
        mvc.perform(get("/").with(user("alice")))
                .andExpect(content().string(containsString("還沒有熱門的店")));
    }

    // ---------- /api/cafes/by-place ----------

    @Test
    void lookupReturnsOnlyPlacesAlreadyInCoFin() throws Exception {
        Long id = cafeService.create("貓下去", null, PLACE_A, alice.getId()).id();
        cafeService.addTag(id, alice.getId(), "安靜");

        mvc.perform(get("/api/cafes/by-place").param("ids", PLACE_A, PLACE_B).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].placeId").value(PLACE_A))
                .andExpect(jsonPath("$[0].cafeId").value(id))
                .andExpect(jsonPath("$[0].tags[0].name").value("安靜"))
                .andExpect(jsonPath("$[0].tags[0].count").value(1));
    }

    @Test
    void lookupWithNoIdsReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/cafes/by-place").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void lookupRejectsMalformedOrTooManyIds() throws Exception {
        mvc.perform(get("/api/cafes/by-place").param("ids", "bad id!").with(user("alice")))
                .andExpect(status().isBadRequest());

        String[] tooMany = IntStream.range(0, 21).mapToObj(i -> PLACE_A.substring(0, 20) + String.format("%07d", i))
                .toArray(String[]::new);
        mvc.perform(get("/api/cafes/by-place").param("ids", tooMany).with(user("alice")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupRequiresLogin() throws Exception {
        mvc.perform(get("/api/cafes/by-place").param("ids", PLACE_A))
                .andExpect(redirectedUrl("/login"));
    }
}
