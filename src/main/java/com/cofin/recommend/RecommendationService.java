package com.cofin.recommend;

import com.cofin.cafe.CafeService;
import com.cofin.cafe.TagCount;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Location-free recommendations from Co-Fin's own data. "Nearby" recommendations come from
 * Google Places in the browser, because cafe coordinates are not stored (see README).
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    static final int POPULAR_LIMIT = 6;

    private final RecommendationRepository recommendations;
    private final CafeService cafeService;

    public RecommendationService(RecommendationRepository recommendations, CafeService cafeService) {
        this.recommendations = recommendations;
        this.cafeService = cafeService;
    }

    public List<PopularCafeView> popular() {
        List<PopularCafe> popular = recommendations.findPopular(PageRequest.of(0, POPULAR_LIMIT));
        Map<Long, List<TagCount>> tags = cafeService.topTagsFor(popular.stream().map(PopularCafe::id).toList());
        return popular.stream()
                .map(p -> new PopularCafeView(p.id(), p.name(), p.address(), p.saves(), p.votes(),
                        tags.getOrDefault(p.id(), List.of())))
                .toList();
    }
}
