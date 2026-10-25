package com.cofin.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, Long> {

    /** Ids only, so listing a page never reads the image bytes. */
    @Query("""
            select new com.cofin.review.PhotoRef(p.id, p.review.id)
            from ReviewPhoto p
            where p.review.id in :reviewIds
            order by p.id
            """)
    List<PhotoRef> findRefs(Collection<Long> reviewIds);

    long countByReviewId(Long reviewId);

    @Modifying
    @Query("delete from ReviewPhoto p where p.review.id = :reviewId and p.id in :photoIds")
    int deleteFromReview(Long reviewId, Collection<Long> photoIds);

    @Query("select new com.cofin.review.PhotoContent(p.contentType, p.data) from ReviewPhoto p where p.id = :id")
    Optional<PhotoContent> findContent(Long id);
}
