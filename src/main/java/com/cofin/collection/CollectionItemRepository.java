package com.cofin.collection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {

    Optional<CollectionItem> findByCollectionIdAndCafeId(Long collectionId, Long cafeId);

    /** {@code join fetch} loads each item's cafe in the same query. */
    @Query("""
            select i from CollectionItem i join fetch i.cafe
            where i.collection.id = :collectionId
            order by i.addedAt desc, i.id desc
            """)
    List<CollectionItem> findWithCafe(Long collectionId);

    @Query("""
            select new com.cofin.collection.CollectionRef(c.id, c.name)
            from CollectionItem i join i.collection c
            where i.cafe.id = :cafeId and c.owner.id = :ownerId
            order by c.name
            """)
    List<CollectionRef> findCollectionsContaining(Long cafeId, Long ownerId);
}
