package com.cofin.cafe;

import com.cofin.common.InvalidInputException;
import com.cofin.common.Names;
import com.cofin.common.NotFoundException;
import com.cofin.user.AppUserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CafeService {

    static final int SEARCH_LIMIT = 50;
    static final int POPULAR_TAG_LIMIT = 20;
    static final int TAGS_PER_CAFE_IN_LIST = 5;
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

    public List<CafeSummary> search(String query, String tag) {
        String pattern = "%" + Names.escapeLike(query == null ? "" : query.strip().toLowerCase(Locale.ROOT)) + "%";
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT);

        List<Cafe> found;
        if (tag == null || tag.isBlank()) {
            found = cafes.searchByName(pattern, page);
        } else {
            Optional<String> normalized = tryNormalizeTag(tag);
            if (normalized.isEmpty()) {
                return List.of();
            }
            found = cafes.searchByNameAndTag(pattern, normalized.get(), page);
        }

        Map<Long, List<TagCount>> tagsByCafe = tagCountsFor(found.stream().map(Cafe::getId).toList());
        return found.stream()
                .map(c -> new CafeSummary(c.getId(), c.getName(), c.getAddress(),
                        tagsByCafe.getOrDefault(c.getId(), List.of()).stream()
                                .limit(TAGS_PER_CAFE_IN_LIST)
                                .toList()))
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
     * Reuses an existing cafe instead of duplicating it: the same Google place id,
     * or (for cafes typed in by hand) the same name and address, case-insensitive.
     */
    @Transactional
    public CreateResult create(String rawName, String rawAddress, String rawPlaceId, Long userId) {
        String name = Names.required(rawName, 100, "店名");
        String address = Names.optional(rawAddress, 200, "地址");
        String placeId = Names.optional(rawPlaceId, 255, "Google 地點");

        Optional<Cafe> existing;
        if (placeId != null) {
            existing = cafes.findByGooglePlaceId(placeId);
        } else if (address == null) {
            existing = cafes.findFirstByNameIgnoreCaseAndAddressIsNull(name);
        } else {
            existing = cafes.findFirstByNameIgnoreCaseAndAddressIgnoreCase(name, address);
        }
        if (existing.isPresent()) {
            return new CreateResult(existing.get().getId(), false);
        }
        Cafe saved = cafes.save(new Cafe(name, address, placeId, users.getReferenceById(userId)));
        return new CreateResult(saved.getId(), true);
    }

    public CafeDetail detail(Long cafeId, Long userId) {
        Cafe cafe = findCafe(cafeId);
        Set<String> mine = Set.copyOf(cafeTags.findTagNames(cafeId, userId));
        List<TagView> tagViews = cafeTags.countByCafeIds(List.of(cafeId)).stream()
                .map(tc -> new TagView(tc.name(), tc.count(), mine.contains(tc.name())))
                .toList();
        return new CafeDetail(cafe.getId(), cafe.getName(), cafe.getAddress(), cafe.getGooglePlaceId(),
                mapsUrl(cafe), tagViews);
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

    private Map<Long, List<TagCount>> tagCountsFor(List<Long> cafeIds) {
        if (cafeIds.isEmpty()) {
            return Map.of();
        }
        // Rows arrive sorted by count, and groupingBy into lists keeps that order per cafe.
        return cafeTags.countByCafeIds(cafeIds).stream()
                .collect(Collectors.groupingBy(CafeTagCount::cafeId, LinkedHashMap::new,
                        Collectors.mapping(c -> new TagCount(c.name(), c.count()), Collectors.toList())));
    }

    private static Optional<String> tryNormalizeTag(String raw) {
        try {
            return Optional.of(Names.tag(raw));
        } catch (InvalidInputException e) {
            return Optional.empty();
        }
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
