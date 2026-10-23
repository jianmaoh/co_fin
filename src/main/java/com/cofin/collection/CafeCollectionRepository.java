package com.cofin.collection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CafeCollectionRepository extends JpaRepository<CafeCollection, Long> {

    /** Looking up by id AND owner is the authorization check: other users' lists are simply not found. */
    Optional<CafeCollection> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndName(Long ownerId, String name);

    @Query("""
            select new com.cofin.collection.CollectionSummary(c.id, c.name, count(i.id))
            from CafeCollection c left join CollectionItem i on i.collection = c
            where c.owner.id = :ownerId
            group by c.id, c.name, c.createdAt
            order by c.createdAt, c.id
            """)
    List<CollectionSummary> summarize(Long ownerId);
}
