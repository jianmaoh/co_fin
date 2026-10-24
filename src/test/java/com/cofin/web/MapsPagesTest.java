package com.cofin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cofin.cafe.CafeRepository;
import com.cofin.cafe.CafeService;
import com.cofin.user.AppUser;
import com.cofin.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Pages when a Google Maps key IS configured (CafeAndCollectionFlowTest covers the no-key case). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:maps;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "cofin.google-maps.api-key=test-browser-key",
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MapsPagesTest {

    static final String PLACE_ID = "ChIJN1t_tDeuEmsRUsoyG83frY4";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService userService;

    @Autowired
    CafeService cafeService;

    @Autowired
    CafeRepository cafes;

    AppUser alice;

    @BeforeEach
    void setUp() {
        for (String table : new String[] {"collection_item", "cafe_collection", "cafe_tag", "tag", "cafe", "app_user"}) {
            jdbc.update("delete from " + table);
        }
        alice = userService.register("alice", "secret123");
    }

    @Test
    void newCafePageLoadsPlaceSearch() throws Exception {
        mvc.perform(get("/cafes/new").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"place-search\"")))
                .andExpect(content().string(containsString("/js/maps.js")))
                .andExpect(content().string(containsString(
                        "https://maps.googleapis.com/maps/api/js?key=test-browser-key&amp;loading=async&amp;callback=coFinMapsReady")));
    }

    @Test
    void cafeFromGoogleShowsMap() throws Exception {
        Long id = cafeService.create("貓下去", "台北市大安區", PLACE_ID, alice.getId()).id();

        mvc.perform(get("/cafes/" + id).with(user("alice")))
                .andExpect(content().string(containsString("data-place-id=\"" + PLACE_ID + "\"")))
                .andExpect(content().string(containsString("maps.googleapis.com/maps/api/js")))
                .andExpect(content().string(containsString("query_place_id=" + PLACE_ID)));
    }

    @Test
    void handTypedCafeHasNoMapAndLoadsNoScript() throws Exception {
        Long id = cafeService.create("手動咖啡", null, alice.getId()).id();

        mvc.perform(get("/cafes/" + id).with(user("alice")))
                .andExpect(content().string(not(containsString("cafe-map"))))
                .andExpect(content().string(not(containsString("maps.googleapis.com"))));
    }

    @Test
    void samePlaceIdIsReusedEvenWithDifferentName() throws Exception {
        mvc.perform(post("/cafes").with(user("alice")).with(csrf())
                        .param("name", "貓下去")
                        .param("address", "台北市大安區")
                        .param("placeId", PLACE_ID))
                .andExpect(flash().attribute("message", "已新增咖啡廳"));
        Long id = cafes.findByGooglePlaceId(PLACE_ID).orElseThrow().getId();

        mvc.perform(post("/cafes").with(user("alice")).with(csrf())
                        .param("name", "Meowvelous")
                        .param("address", "Da'an, Taipei")
                        .param("placeId", PLACE_ID))
                .andExpect(redirectedUrl("/cafes/" + id))
                .andExpect(flash().attribute("message", "這家咖啡廳已經有人新增過了"));
        assertThat(cafes.count()).isEqualTo(1);
    }

    @Test
    void malformedPlaceIdIsRejected() throws Exception {
        mvc.perform(post("/cafes").with(user("alice")).with(csrf())
                        .param("name", "貓下去")
                        .param("placeId", "not a place id!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Google 地點資料有誤")));
        assertThat(cafes.count()).isZero();
    }
}
