package com.cofin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cofin.cafe.CafeService;
import com.cofin.review.ReviewService;
import com.cofin.user.AppUser;
import com.cofin.user.UserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reviews;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReviewFlowTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 5, 6, 7};
    static final String PLACE_ID = "ChIJReviewReviewReviewRevie";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService userService;

    @Autowired
    CafeService cafeService;

    @Autowired
    ReviewService reviewService;

    AppUser alice;
    AppUser bob;
    Long cafeId;

    @BeforeEach
    void setUp() {
        for (String table : new String[] {"review_photo", "review", "collection_item", "cafe_collection",
                "cafe_tag", "tag", "cafe", "app_user"}) {
            jdbc.update("delete from " + table);
        }
        alice = userService.register("alice", "secret123");
        bob = userService.register("bob", "secret123");
        cafeId = cafeService.create("貓下去", "台北市大安區", PLACE_ID, alice.getId()).id();
    }

    MockMultipartHttpServletRequestBuilder reviewRequest() {
        return multipart("/cafes/" + cafeId + "/review");
    }

    static MockMultipartFile photo(String name, String declaredType, byte[] data) {
        return new MockMultipartFile("photos", name, declaredType, data);
    }

    static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Taipei"));
    }

    long reviewCount() {
        return jdbc.queryForObject("select count(*) from review", Long.class);
    }

    @Test
    void reviewWithPhotosAppearsInDrawerAndPhotosAreServed() throws Exception {
        mvc.perform(reviewRequest()
                        .file(photo("a.jpg", "image/jpeg", JPEG))
                        .file(photo("b.png", "image/png", PNG))
                        .param("rating", "4")
                        .param("content", "拿鐵很好喝\n二樓有插座")
                        .param("visitedOn", today().minusDays(3).toString())
                        .with(user("alice")).with(csrf()))
                .andExpect(redirectedUrl("/cafes/" + cafeId + "#my-review"))
                .andExpect(flash().attribute("message", "已儲存你的評論"));

        List<Long> photoIds = reviewService.myReview(cafeId, alice.getId()).orElseThrow().photoIds();
        assertThat(photoIds).hasSize(2);

        mvc.perform(get("/cafes/" + cafeId).with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-participant=\"participant-0\"")))
                .andExpect(content().string(containsString("拿鐵很好喝")))
                .andExpect(content().string(containsString("/photos/" + photoIds.getFirst())))
                .andExpect(content().string(containsString("★★★★☆")));

        mvc.perform(get("/photos/" + photoIds.getFirst()).with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void storedTypeComesFromBytesNotFromTheBrowser() throws Exception {
        // A real PNG uploaded with a misleading name and type is stored and served as PNG.
        mvc.perform(reviewRequest().file(photo("x.jpg", "image/jpeg", PNG)).param("rating", "5")
                .with(user("alice")).with(csrf()));
        Long id = reviewService.myReview(cafeId, alice.getId()).orElseThrow().photoIds().getFirst();

        mvc.perform(get("/photos/" + id).with(user("alice")))
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void invalidReviewsAreRejectedAndNothingIsSaved() throws Exception {
        mvc.perform(reviewRequest().with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "請選擇 1 到 5 顆星"));
        mvc.perform(reviewRequest().param("rating", "6").with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "請選擇 1 到 5 顆星"));
        mvc.perform(reviewRequest().param("rating", "3").param("visitedOn", today().plusDays(1).toString())
                        .with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "造訪日期不能是未來"));
        mvc.perform(reviewRequest().param("rating", "3").param("visitedOn", "not-a-date")
                        .with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "造訪日期格式不正確"));
        mvc.perform(reviewRequest().file(photo("evil.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                        .param("rating", "3").with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "只接受 JPEG、PNG 或 WebP 照片"));
        mvc.perform(reviewRequest()
                        .file(photo("1.jpg", "image/jpeg", JPEG)).file(photo("2.jpg", "image/jpeg", JPEG))
                        .file(photo("3.jpg", "image/jpeg", JPEG)).file(photo("4.jpg", "image/jpeg", JPEG))
                        .param("rating", "3").with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "每則評論最多 3 張照片"));

        assertThat(reviewCount()).isZero();
    }

    @Test
    void editingReplacesTheReviewAndManagesPhotos() throws Exception {
        mvc.perform(reviewRequest().file(photo("a.jpg", "image/jpeg", JPEG)).file(photo("b.png", "image/png", PNG))
                .param("rating", "5").param("content", "第一次").with(user("alice")).with(csrf()));
        List<Long> before = reviewService.myReview(cafeId, alice.getId()).orElseThrow().photoIds();

        // Bob's review, whose photo id Alice will try to delete through her own form.
        mvc.perform(reviewRequest().file(photo("bob.jpg", "image/jpeg", JPEG)).param("rating", "3")
                .with(user("bob")).with(csrf()));
        Long bobsPhoto = reviewService.myReview(cafeId, bob.getId()).orElseThrow().photoIds().getFirst();

        mvc.perform(reviewRequest().file(photo("c.jpg", "image/jpeg", JPEG))
                        .param("rating", "2").param("content", "第二次去比較吵")
                        .param("deletePhotoIds", before.getFirst().toString(), bobsPhoto.toString())
                        .with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("message", "已儲存你的評論"));

        var edited = reviewService.myReview(cafeId, alice.getId()).orElseThrow();
        assertThat(edited.rating()).isEqualTo(2);
        assertThat(edited.content()).isEqualTo("第二次去比較吵");
        assertThat(edited.photoIds()).hasSize(2).doesNotContain(before.getFirst());
        assertThat(reviewService.myReview(cafeId, bob.getId()).orElseThrow().photoIds()).containsExactly(bobsPhoto);
        assertThat(reviewCount()).isEqualTo(2);

        // Already at 2 photos: adding 2 more would exceed the limit of 3.
        mvc.perform(reviewRequest().file(photo("d.jpg", "image/jpeg", JPEG)).file(photo("e.jpg", "image/jpeg", JPEG))
                        .param("rating", "2").with(user("alice")).with(csrf()))
                .andExpect(flash().attribute("error", "每則評論最多 3 張照片"));
        assertThat(reviewService.myReview(cafeId, alice.getId()).orElseThrow().photoIds()).hasSize(2);
    }

    @Test
    void deletingReviewRemovesItsPhotos() throws Exception {
        mvc.perform(reviewRequest().file(photo("a.jpg", "image/jpeg", JPEG)).param("rating", "4")
                .with(user("alice")).with(csrf()));

        mvc.perform(post("/cafes/" + cafeId + "/review/delete").with(user("alice")).with(csrf()))
                .andExpect(redirectedUrl("/cafes/" + cafeId));

        assertThat(reviewCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from review_photo", Long.class)).isZero();
    }

    @Test
    void ratingsFeedTheCafePageAndTheNearbyLookup() throws Exception {
        reviewService.save(cafeId, alice.getId(), 4, null, null, null, null);
        reviewService.save(cafeId, bob.getId(), 5, null, null, null, null);

        assertThat(reviewService.ratingFor(cafeId).average()).isEqualTo(4.5);
        mvc.perform(get("/cafes/" + cafeId).with(user("alice")))
                .andExpect(content().string(containsString("2 則評論")));
        mvc.perform(get("/api/cafes/by-place").param("ids", PLACE_ID).with(user("alice")))
                .andExpect(jsonPath("$[0].averageRating").value(4.5))
                .andExpect(jsonPath("$[0].reviewCount").value(2));
    }

    @Test
    void tagOnlyUsersAreListedToo() throws Exception {
        reviewService.save(cafeId, alice.getId(), 4, "不錯", null, null, null);
        cafeService.addTag(cafeId, alice.getId(), "安靜");
        cafeService.addTag(cafeId, bob.getId(), "有插座");

        assertThat(reviewService.participants(cafeId))
                .extracting(p -> p.username(), p -> p.review() != null, p -> p.tags())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alice", true, List.of("安靜")),
                        org.assertj.core.groups.Tuple.tuple("bob", false, List.of("有插座")));
        mvc.perform(get("/cafes/" + cafeId).with(user("alice")))
                .andExpect(content().string(containsString("只加了標籤")));
    }

    @Test
    void photosRequireLogin() throws Exception {
        mvc.perform(get("/photos/1")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void unknownPhotoIs404() throws Exception {
        mvc.perform(get("/photos/999999").with(user("alice"))).andExpect(status().isNotFound());
    }
}
