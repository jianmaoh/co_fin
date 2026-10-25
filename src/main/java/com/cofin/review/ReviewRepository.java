package com.cofin.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByCafeIdAndUserId(Long cafeId, Long userId);

    @Query("""
            select r from Review r join fetch r.user
            where r.cafe.id = :cafeId
            order by r.updatedAt desc, r.id desc
            """)
    List<Review> findByCafeWithUser(Long cafeId);

    @Query("""
            select new com.cofin.review.RatingSummary(r.cafe.id, avg(r.rating), count(r.id))
            from Review r
            where r.cafe.id in :cafeIds
            group by r.cafe.id
            """)
    List<RatingSummary> summarize(Collection<Long> cafeIds);
}
