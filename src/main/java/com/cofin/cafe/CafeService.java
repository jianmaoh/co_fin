package com.cofin.cafe;

import com.cofin.common.InvalidInputException;
import com.cofin.common.Names;
import com.cofin.common.NotFoundException;
import com.cofin.user.AppUser;
import com.cofin.user.AppUserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CafeService {

    static final int SEARCH_LIMIT = 50;
    static final int POPULAR_TAG_LIMIT = 20;
    static final int TAGS_PER_CAFE_IN_LIST = 5;
    static final int TAGS_PER_CAFE_IN_SEARCH = 6;
    static final int MAX_KEYWORDS = 5;
    static final int MAX_TAGS_PER_USER_PER_CAFE = 10;

    private final CafeRepository cafes;
    private final TagRepository tags;
    private final CafeTagRepository cafeTags;
    private final AppUserRepository users;

    public CafeService(CafeRepository cafes, TagRepository tags, CafeTagRepository cafeTags,
                       AppUserRepository users) {
        this.cafes = cafes;
        this.tags = tags;
        this.cafeTags = cafeTags;
        this.users = users;
    }

    /**
     * Keyword search over tags and names. Each keyword must match (AND) either part of a tag or part
     * of the cafe name, so "巴斯克" finds #巴斯克蛋糕 and "巴斯克 插座" needs both. Results are ranked
     * by how many votes their matching tags have. An empty query lists the newest cafes.
     */
    public List<SearchResult> search(String query) {
        List<String> keywords = keywords(query);
        List<Cafe> found;
        if (keywords.isEmpty()) {
            found = cafes.searchByName("%", PageRequest.of(0, SEARCH_LIMIT));
        } else {
            Set<Long> ids = null;
            for (String keyword : keywords) {
                List<Long> matching = cafes.findIdsMatching("%" + Names.escapeLike(keyword) + "%");
                if (ids == null) {
                    ids = new LinkedHashSet<>(matching);
                } else {
                    ids.retainAll(matching);
                }
                if (ids.isEmpty()) {
                    return List.of();
                }
            }
            found = cafes.findAllById(ids);
        }

        Map<Long, List<CafeTagCount>> tagsByCafe = found.isEmpty() ? Map.of()
                : cafeTags.countByCafeIds(found.stream().map(Cafe::getId).toList()).stream()
                        .collect(Collectors.groupingBy(CafeTagCount::cafeId));
        Stream<SearchResult> results = found.stream()
                .map(c -> toSearchResult(c, tagsByCafe.getOrDefault(c.getId(), List.of()), keywords));
        if (!keywords.isEmpty()) {
            results = results.sorted(Comparator.comparingLong(SearchResult::score).reversed()
                    .thenComparing(SearchResult::name));
        }
        return results.limit(SEARCH_LIMIT).toList();
    }

    /** Splits on spaces, commas and 、; drops a leading '#'; at most {@value #MAX_KEYWORDS} keywords. */
    static List<String> keywords(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,，、]+"))
                .map(k -> k.replaceFirst("^#+", "").strip())
                .filter(k -> !k.isEmpty())
                .map(k -> k.length() > Names.TAG_MAX_LENGTH ? k.substring(0, Names.TAG_MAX_LENGTH) : k)
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
    }

    private static SearchResult toSearchResult(Cafe cafe, List<CafeTagCount> counts, List<String> keywords) {
        List<TagMatch> tags = counts.stream()
                .map(c -> new TagMatch(c.name(), c.count(), keywords.stream().anyMatch(c.name()::contains)))
                // matching tags first, then by votes (counts arrive sorted by votes)
                .sorted(Comparator.comparing(TagMatch::matched).reversed())
                .limit(TAGS_PER_CAFE_IN_SEARCH)
                .toList();
        long score = counts.stream()
                .filter(c -> keywords.stream().anyMatch(c.name()::contains))
                .mapToLong(CafeTagCount::count)
                .sum();
        return new SearchResult(cafe.getId(), cafe.getName(), cafe.getAddress(), tags, score);
    }

    /** Which of these Google places are already in Co-Fin, with their top tags. */
    public List<PlaceMatch> findByPlaceIds(Collection<String> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        List<Cafe> found = cafes.findByGooglePlaceIdIn(placeIds);
        Map<Long, List<TagCount>> tagsByCafe = topTagsFor(found.stream().map(Cafe::getId).toList());
        return found.stream()
                .map(c -> new PlaceMatch(c.getGooglePlaceId(), c.getId(), tagsByCafe.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    public List<TagCount> popularTags() {
        return cafeTags.findPopular(PageRequest.of(0, POPULAR_TAG_LIMIT));
    }

    @Transactional
    public CreateResult create(String rawName, String rawAddress, Long userId) {
        return create(rawName, rawAddress, null, userId);
    }

    /**
     * Name and address are both required. Reuses an existing cafe instead of duplicating it:
     * the same Google place id, or the same name and address (case-insensitive).
     */
    @Transactional
    public CreateResult create(String rawName, String rawAddress, String rawPlaceId, Long userId) {
        String name = Names.required(rawName, 100, "店名");
        String address = Names.required(rawAddress, 200, "地址");
        String placeId = Names.optional(rawPlaceId, 255, "Google 地點");

        Optional<Cafe> existing = placeId != null
                ? cafes.findByGooglePlaceId(placeId)
                : cafes.findFirstByNameIgnoreCaseAndAddressIgnoreCase(name, address);
        if (existing.isPresent()) {
            return new CreateResult(existing.get().getId(), false);
        }
        Cafe saved = cafes.save(new Cafe(name, address, placeId, users.getReferenceById(userId)));
        return new CreateResult(saved.getId(), true);
    }

    public CafeDetail detail(Long cafeId, Long userId) {
        Cafe cafe = findCafe(cafeId);
        AppUser viewer = users.findById(userId).orElseThrow(() -> new NotFoundException("找不到使用者"));
        Set<String> mine = Set.copyOf(cafeTags.findTagNames(cafeId, userId));
        List<TagView> tagViews = cafeTags.countByCafeIds(List.of(cafeId)).stream()
                .map(tc -> new TagView(tc.name(), tc.count(), mine.contains(tc.name())))
                .toList();
        String createdBy = cafe.getCreatedBy() == null ? null : cafe.getCreatedBy().getUsername();
        return new CafeDetail(cafe.getId(), cafe.getName(), cafe.getAddress(), cafe.getGooglePlaceId(),
                mapsUrl(cafe), tagViews, cafe.isApproved(), createdBy,
                cafe.canBeDeletedBy(viewer), viewer.isAdmin() && !cafe.isApproved());
    }

    /** The database cascades the delete to tags, collection items and reviews. */
    @Transactional
    public void delete(Long cafeId, AppUser actor) {
        Cafe cafe = findCafe(cafeId);
        if (!cafe.canBeDeletedBy(actor)) {
            throw new AccessDeniedException(cafe.isApproved()
                    ? "已核准的咖啡廳只能由管理員刪除"
                    : "只有新增這家店的人或管理員可以刪除");
        }
        cafes.delete(cafe);
    }

    @Transactional
    public void approve(Long cafeId, AppUser actor) {
        if (!actor.isAdmin()) {
            throw new AccessDeniedException("只有管理員可以核准咖啡廳");
        }
        Cafe cafe = findCafe(cafeId);
        if (!cafe.isApproved()) {
            cafe.approve(actor);
        }
    }

    public List<PendingCafe> pending() {
        return cafes.findPending();
    }

    /** Idempotent: adding a tag you already added is a no-op. */
    @Transactional
    public void addTag(Long cafeId, Long userId, String rawTag) {
        String name = Names.tag(rawTag);
        Cafe cafe = findCafe(cafeId);
        Tag tag = tags.findByName(name).orElseGet(() -> tags.save(new Tag(name)));

        if (cafeTags.existsByCafeIdAndTagIdAndUserId(cafeId, tag.getId(), userId)) {
            return;
        }
        if (cafeTags.countByCafeIdAndUserId(cafeId, userId) >= MAX_TAGS_PER_USER_PER_CAFE) {
            throw new InvalidInputException("每家咖啡廳最多加 " + MAX_TAGS_PER_USER_PER_CAFE + " 個標籤");
        }
        cafeTags.save(new CafeTag(cafe, tag, users.getReferenceById(userId)));
    }

    /** Only removes the caller's own vote; other people's identical tags stay. */
    @Transactional
    public void removeTag(Long cafeId, Long userId, String rawTag) {
        String name = Names.tag(rawTag);
        cafeTags.findByCafeIdAndTagNameAndUserId(cafeId, name, userId).ifPresent(cafeTags::delete);
    }

    private Cafe findCafe(Long cafeId) {
        return cafes.findById(cafeId).orElseThrow(() -> new NotFoundException("找不到這家咖啡廳"));
    }

    /** The most-agreed tags of each cafe, in one query for all of them (avoids N+1). */
    public Map<Long, List<TagCount>> topTagsFor(List<Long> cafeIds) {
        if (cafeIds.isEmpty()) {
            return Map.of();
        }
        // Rows arrive sorted by count, and groupingBy into lists keeps that order per cafe.
        Map<Long, List<TagCount>> all = cafeTags.countByCafeIds(cafeIds).stream()
                .collect(Collectors.groupingBy(CafeTagCount::cafeId, LinkedHashMap::new,
                        Collectors.mapping(c -> new TagCount(c.name(), c.count()), Collectors.toList())));
        all.replaceAll((id, tags) -> tags.stream().limit(TAGS_PER_CAFE_IN_LIST).toList());
        return all;
    }

    /** A plain Google Maps search link; it needs no API key. */
    static String mapsUrl(Cafe cafe) {
        String query = cafe.getAddress() == null ? cafe.getName() : cafe.getName() + " " + cafe.getAddress();
        String url = "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        if (cafe.getGooglePlaceId() != null) {
            url += "&query_place_id=" + URLEncoder.encode(cafe.getGooglePlaceId(), StandardCharsets.UTF_8);
        }
        return url;
    }
}
