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
import com.cofin.collection.CafeCollectionRepository;
import com.cofin.collection.CollectionService;
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

/** Feature test: cafes, tags and collections through HTTP, on in-memory H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cafeflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CafeAndCollectionFlowTest {

    static final RequestPostProcessor ALICE = user("alice");
    static final RequestPostProcessor BOB = user("bob");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService userService;

    @Autowired
    AppUserRepository users;

    @Autowired
    CafeRepository cafes;

    @Autowired
    CafeCollectionRepository collections;

    @Autowired
    CafeService cafeService;

    @Autowired
    CollectionService collectionService;

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

    // ---------- cafes ----------

    @Test
    void createCafeRedirectsToItsPageAndDeduplicates() throws Exception {
        mvc.perform(post("/cafes").with(ALICE).with(csrf())
                        .param("name", "貓下去")
                        .param("address", "台北市大安區"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "已新增咖啡廳"));
        Long id = cafes.findAll().getFirst().getId();

        // Same name and address with different spacing/case: reuses the existing cafe.
        mvc.perform(post("/cafes").with(BOB).with(csrf())
                        .param("name", "  貓下去 ")
                        .param("address", "台北市大安區"))
                .andExpect(redirectedUrl("/cafes/" + id))
                .andExpect(flash().attribute("message", "這家咖啡廳已經有人新增過了"));
        assertThat(cafes.count()).isEqualTo(1);

        mvc.perform(get("/cafes/" + id).with(BOB))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("貓下去")))
                .andExpect(content().string(containsString("google.com/maps/search")));
    }

    @Test
    void createCafeWithoutNameShowsError() throws Exception {
        mvc.perform(post("/cafes").with(ALICE).with(csrf()).param("name", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("請輸入店名")));
        assertThat(cafes.count()).isZero();
    }

    @Test
    void withoutMapsKeyThePageOffersManualInputOnly() throws Exception {
        mvc.perform(get("/cafes/new").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"name\"")))
                .andExpect(content().string(not(containsString("place-search"))))
                .andExpect(content().string(not(containsString("maps.googleapis.com"))));
    }

    @Test
    void createCafeWithoutAddressShowsError() throws Exception {
        mvc.perform(post("/cafes").with(ALICE).with(csrf()).param("name", "沒有地址"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("請輸入地址")));
        assertThat(cafes.count()).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cafeService.create("沒有地址", " ", alice.getId()))
                .hasMessageContaining("地址不能是空的");
    }

    @Test
    void unknownCafeIs404() throws Exception {
        mvc.perform(get("/cafes/999999").with(ALICE)).andExpect(status().isNotFound());
    }

    @Test
    void cafePagesRequireLogin() throws Exception {
        mvc.perform(get("/search")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/collections")).andExpect(redirectedUrl("/login"));
    }

    // ---------- tags ----------

    @Test
    void tagsAreCountedPerUserAndRemovingOnlyAffectsYourOwn() throws Exception {
        Long id = cafeService.create("貓下去", "台北市大安區", alice.getId()).id();

        mvc.perform(post("/cafes/" + id + "/tags").with(ALICE).with(csrf()).param("name", "#WiFi"))
                .andExpect(redirectedUrl("/cafes/" + id));
        mvc.perform(post("/cafes/" + id + "/tags").with(ALICE).with(csrf()).param("name", "wifi")); // no-op
        mvc.perform(post("/cafes/" + id + "/tags").with(BOB).with(csrf()).param("name", "WIFI"));
        mvc.perform(post("/cafes/" + id + "/tags").with(BOB).with(csrf()).param("name", "安靜"));

        assertThat(cafeService.detail(id, alice.getId()).tags())
                .extracting("name", "count", "mine")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("wifi", 2L, true),
                        org.assertj.core.groups.Tuple.tuple("安靜", 1L, false));

        // Alice tries to remove "安靜", which only Bob added: nothing changes.
        mvc.perform(post("/cafes/" + id + "/tags/remove").with(ALICE).with(csrf()).param("name", "安靜"));
        // Alice removes her own "wifi" vote; Bob's stays.
        mvc.perform(post("/cafes/" + id + "/tags/remove").with(ALICE).with(csrf()).param("name", "wifi"));

        assertThat(cafeService.detail(id, alice.getId()).tags())
                .extracting("name", "count", "mine")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("wifi", 1L, false),
                        org.assertj.core.groups.Tuple.tuple("安靜", 1L, false));
    }

    @Test
    void invalidTagShowsFlashError() throws Exception {
        Long id = cafeService.create("貓下去", "台北市大安區", alice.getId()).id();

        mvc.perform(post("/cafes/" + id + "/tags").with(ALICE).with(csrf()).param("name", "#"))
                .andExpect(redirectedUrl("/cafes/" + id))
                .andExpect(flash().attribute("error", "標籤不能是空的"));
    }

    @Test
    void tagLimitPerUserPerCafe() {
        Long id = cafeService.create("貓下去", "台北市大安區", alice.getId()).id();
        for (int i = 0; i < 10; i++) {
            cafeService.addTag(id, alice.getId(), "tag" + i);
        }

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cafeService.addTag(id, alice.getId(), "one-more"))
                .hasMessageContaining("最多加 10 個標籤");
        // Re-adding an existing tag is still fine at the limit.
        cafeService.addTag(id, alice.getId(), "tag0");
    }

    @Test
    void searchMatchesPartsOfTagsAndNames() throws Exception {
        Long basque = cafeService.create("甜點咖啡", "台北市大安區", alice.getId()).id();
        Long quiet = cafeService.create("安靜咖啡", "台北市信義區", alice.getId()).id();
        Long both = cafeService.create("角落", "台北市中山區", alice.getId()).id();
        cafeService.create("100% Coffee", "台北市松山區", alice.getId());
        cafeService.addTag(basque, alice.getId(), "巴斯克蛋糕");
        cafeService.addTag(quiet, alice.getId(), "安靜");
        cafeService.addTag(both, alice.getId(), "巴斯克");
        cafeService.addTag(both, bob.getId(), "巴斯克");
        cafeService.addTag(both, alice.getId(), "有插座");

        // Part of a tag: "巴斯克" finds #巴斯克 and #巴斯克蛋糕; more votes rank higher.
        assertThat(cafeService.search("巴斯克")).extracting("name").containsExactly("角落", "甜點咖啡");
        assertThat(cafeService.search("#巴斯克")).extracting("name").containsExactly("角落", "甜點咖啡");
        assertThat(cafeService.search("巴斯克").getFirst().tags())
                .extracting("name", "matched")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("巴斯克", true),
                        org.assertj.core.groups.Tuple.tuple("有插座", false));
        // Several keywords must all match (AND), across tags and names.
        assertThat(cafeService.search("巴斯克 插座")).extracting("name").containsExactly("角落");
        assertThat(cafeService.search("巴斯克、咖啡")).extracting("name").containsExactly("甜點咖啡");
        assertThat(cafeService.search("巴斯克 不存在")).isEmpty();
        // Names match too, and "%" is literal, not a wildcard.
        assertThat(cafeService.search("咖啡")).extracting("name").containsExactlyInAnyOrder("甜點咖啡", "安靜咖啡");
        assertThat(cafeService.search("100%")).extracting("name").containsExactly("100% Coffee");
        assertThat(cafeService.search("%")).extracting("name").containsExactly("100% Coffee");
        // Empty query lists everything, newest first.
        assertThat(cafeService.search("  ")).hasSize(4).first().extracting("name").isEqualTo("100% Coffee");

        mvc.perform(get("/search").param("q", "巴斯克").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("角落")))
                .andExpect(content().string(containsString("甜點咖啡")))
                .andExpect(content().string(not(containsString("安靜咖啡"))))
                .andExpect(content().string(containsString("「巴斯克」的結果：2 家")));
    }

    @Test
    void oldCafesListRedirectsToSearch() throws Exception {
        mvc.perform(get("/cafes").with(ALICE)).andExpect(redirectedUrl("/search"));
        mvc.perform(get("/cafes").param("q", "巴斯克").with(ALICE))
                .andExpect(redirectedUrl("/search?q=%E5%B7%B4%E6%96%AF%E5%85%8B"));
    }

    @Test
    void navShowsHomeCollectionsAndSearch() throws Exception {
        mvc.perform(get("/search").with(ALICE))
                .andExpect(content().string(containsString(">首頁</a>")))
                .andExpect(content().string(containsString(">我的清單</a>")))
                .andExpect(content().string(containsString(">搜尋</a>")))
                .andExpect(content().string(not(containsString(">咖啡廳</a>"))))
                .andExpect(content().string(not(containsString(">附近</a>"))));
    }

    // ---------- collections ----------

    @Test
    void collectionLifecycle() throws Exception {
        Long cafeId = cafeService.create("貓下去", "台北市大安區", alice.getId()).id();

        mvc.perform(post("/collections").with(ALICE).with(csrf()).param("name", "讀書用"))
                .andExpect(status().is3xxRedirection());
        Long listId = collections.findAll().getFirst().getId();

        mvc.perform(post("/collections/items").with(ALICE).with(csrf())
                        .param("collectionId", listId.toString())
                        .param("cafeId", cafeId.toString())
                        .param("note", "二樓有插座"))
                .andExpect(redirectedUrl("/cafes/" + cafeId))
                .andExpect(flash().attribute("message", "已加入清單"));

        mvc.perform(get("/collections/" + listId).with(ALICE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("貓下去")))
                .andExpect(content().string(containsString("二樓有插座")));
        mvc.perform(get("/cafes/" + cafeId).with(ALICE))
                .andExpect(content().string(containsString("已收藏在")));
        assertThat(collectionService.listFor(alice.getId())).extracting("itemCount").containsExactly(1L);

        mvc.perform(post("/collections/" + listId + "/rename").with(ALICE).with(csrf()).param("name", "週末"))
                .andExpect(redirectedUrl("/collections/" + listId));
        mvc.perform(post("/collections/" + listId + "/items/" + cafeId + "/delete").with(ALICE).with(csrf()))
                .andExpect(redirectedUrl("/collections/" + listId));
        assertThat(collectionService.detail(alice.getId(), listId).items()).isEmpty();

        mvc.perform(post("/collections/" + listId + "/delete").with(ALICE).with(csrf()))
                .andExpect(redirectedUrl("/collections"));
        assertThat(collections.count()).isZero();
    }

    @Test
    void duplicateCollectionNameShowsError() throws Exception {
        collectionService.create(alice.getId(), "讀書用");

        mvc.perform(post("/collections").with(ALICE).with(csrf()).param("name", "讀書用"))
                .andExpect(redirectedUrl("/collections"))
                .andExpect(flash().attribute("error", "你已經有叫「讀書用」的清單了"));

        // A different user may use the same name.
        collectionService.create(bob.getId(), "讀書用");
    }

    @Test
    void otherUsersCannotSeeOrChangeYourCollection() throws Exception {
        Long cafeId = cafeService.create("貓下去", "台北市大安區", alice.getId()).id();
        Long listId = collectionService.create(alice.getId(), "私藏");

        mvc.perform(get("/collections/" + listId).with(BOB)).andExpect(status().isNotFound());
        mvc.perform(post("/collections/" + listId + "/delete").with(BOB).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/collections/" + listId + "/rename").with(BOB).with(csrf()).param("name", "hacked"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/collections/items").with(BOB).with(csrf())
                        .param("collectionId", listId.toString())
                        .param("cafeId", cafeId.toString()))
                .andExpect(status().isNotFound());

        assertThat(collectionService.detail(alice.getId(), listId).name()).isEqualTo("私藏");
        assertThat(collectionService.detail(alice.getId(), listId).items()).isEmpty();
        mvc.perform(get("/collections").with(BOB)).andExpect(content().string(not(containsString("私藏"))));
    }

    @Test
    void deletingUserCascadesTheirDataButKeepsSharedCafes() {
        Long cafeId = cafeService.create("貓下去", "台北市大安區", alice.getId()).id();
        Long listId = collectionService.create(alice.getId(), "讀書用");
        collectionService.addItem(alice.getId(), listId, cafeId, null);
        cafeService.addTag(cafeId, alice.getId(), "安靜");
        cafeService.addTag(cafeId, bob.getId(), "安靜");

        jdbc.update("delete from app_user where username = 'alice'");

        assertThat(collections.count()).isZero();
        assertThat(cafes.findById(cafeId)).isPresent();
        assertThat(jdbc.queryForObject("select created_by from cafe where id = ?", Long.class, cafeId)).isNull();
        assertThat(cafeService.detail(cafeId, bob.getId()).tags())
                .extracting("name", "count")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("安靜", 1L));
    }

    @Test
    void deletedAccountWithLiveSessionIsForbidden() throws Exception {
        users.delete(bob);

        mvc.perform(get("/collections").with(BOB)).andExpect(status().isForbidden());
    }
}
