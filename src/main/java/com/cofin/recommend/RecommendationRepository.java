package com.cofin.recommend;

import com.cofin.cafe.Cafe;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface RecommendationRepository extends Repository<Cafe, Long> {

    /**
     * Cafes ranked by how many lists saved them, then by tag votes.
     *
     * <p>Joining both child tables multiplies rows (saves x votes) before grouping, which is why the
     * counts use {@code distinct}. Fine at this scale; with heavy traffic, keep counters on the cafe row
     * or precompute the ranking instead.
     */
    @Query("""
            select new com.cofin.recommend.PopularCafe(c.id, c.name, c.address,
                       count(distinct i.id), count(distinct ct.id))
            from Cafe c
                left join CollectionItem i on i.cafe = c
                left join CafeTag ct on ct.cafe = c
            group by c.id, c.name, c.address, c.createdAt
            having count(distinct i.id) + count(distinct ct.id) > 0
            order by count(distinct i.id) desc, count(distinct ct.id) desc, c.createdAt desc
            """)
    List<PopularCafe> findPopular(Pageable page);
}
