package com.cofin.cafe;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CafeTagRepository extends JpaRepository<CafeTag, Long> {

    boolean existsByCafeIdAndTagIdAndUserId(Long cafeId, Long tagId, Long userId);

    long countByCafeIdAndUserId(Long cafeId, Long userId);

    Optional<CafeTag> findByCafeIdAndTagNameAndUserId(Long cafeId, String tagName, Long userId);

    /** Tag counts for many cafes in one query, instead of one query per cafe (the N+1 problem). */
    @Query("""
            select new com.cofin.cafe.CafeTagCount(ct.cafe.id, t.name, count(ct.id))
            from CafeTag ct join ct.tag t
            where ct.cafe.id in :cafeIds
            group by ct.cafe.id, t.name
            order by count(ct.id) desc, t.name
            """)
    List<CafeTagCount> countByCafeIds(Collection<Long> cafeIds);

    @Query("""
            select t.name from CafeTag ct join ct.tag t
            where ct.cafe.id = :cafeId and ct.user.id = :userId
            """)
    List<String> findTagNames(Long cafeId, Long userId);

    @Query("""
            select new com.cofin.cafe.TagCount(t.name, count(ct.id))
            from CafeTag ct join ct.tag t
            group by t.name
            order by count(ct.id) desc, t.name
            """)
    List<TagCount> findPopular(Pageable page);
}
