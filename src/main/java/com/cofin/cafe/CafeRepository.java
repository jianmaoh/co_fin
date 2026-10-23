package com.cofin.cafe;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CafeRepository extends JpaRepository<Cafe, Long> {

    Optional<Cafe> findFirstByNameIgnoreCaseAndAddressIgnoreCase(String name, String address);

    Optional<Cafe> findFirstByNameIgnoreCaseAndAddressIsNull(String name);

    /** {@code pattern} must already be lower-cased and LIKE-escaped (see Names.escapeLike). */
    @Query("""
            select c from Cafe c
            where lower(c.name) like :pattern escape '\\'
            order by c.createdAt desc, c.id desc
            """)
    List<Cafe> searchByName(String pattern, Pageable page);

    @Query("""
            select c from Cafe c
            where lower(c.name) like :pattern escape '\\'
              and exists (select ct.id from CafeTag ct where ct.cafe = c and ct.tag.name = :tag)
            order by c.createdAt desc, c.id desc
            """)
    List<Cafe> searchByNameAndTag(String pattern, String tag, Pageable page);
}
