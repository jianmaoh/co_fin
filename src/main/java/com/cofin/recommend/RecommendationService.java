package com.cofin.recommend;

import com.cofin.cafe.CafeService;
import com.cofin.cafe.TagCount;
import com.cofin.review.RatingSummary;
import com.cofin.review.ReviewService;
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
    private final ReviewService reviewService;

    public RecommendationService(RecommendationRepository recommendations, CafeService cafeService,
                                 ReviewService reviewService) {
        this.recommendations = recommendations;
        this.cafeService = cafeService;
        this.reviewService = reviewService;
    }

    public List<PopularCafeView> popular() {
        List<PopularCafe> popular = recommendations.findPopular(PageRequest.of(0, POPULAR_LIMIT));
        List<Long> ids = popular.stream().map(PopularCafe::id).toList();
        Map<Long, List<TagCount>> tags = cafeService.topTagsFor(ids);
        Map<Long, RatingSummary> ratings = reviewService.ratingsFor(ids);
        return popular.stream()
                .map(p -> {
                    RatingSummary r = ratings.getOrDefault(p.id(), RatingSummary.none(p.id()));
                    return new PopularCafeView(p.id(), p.name(), p.address(), p.saves(), p.votes(),
                            tags.getOrDefault(p.id(), List.of()), r.average(), r.count());
                })
                .toList();
    }
}
