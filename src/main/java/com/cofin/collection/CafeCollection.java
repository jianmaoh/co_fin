package com.cofin.collection;

import com.cofin.user.AppUser;
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

/** A user's named list of cafes. Named CafeCollection to avoid clashing with java.util.Collection. */
@Entity
@Table(name = "cafe_collection")
public class CafeCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CafeCollection() {
        // required by JPA
    }

    public CafeCollection(AppUser owner, String name) {
        this.owner = owner;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
