package com.cofin.collection;

import com.cofin.cafe.Cafe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "collection_item")
public class CollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id")
    private CafeCollection collection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cafe_id")
    private Cafe cafe;

    @Column(length = 200)
    private String note;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected CollectionItem() {
        // required by JPA
    }

    public CollectionItem(CafeCollection collection, Cafe cafe, String note) {
        this.collection = collection;
        this.cafe = cafe;
        this.note = note;
        this.addedAt = Instant.now();
    }

    public void changeNote(String note) {
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public CafeCollection getCollection() {
        return collection;
    }

    public Cafe getCafe() {
        return cafe;
    }

    public String getNote() {
        return note;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
