package com.cofin.cafe;

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

@Entity
@Table(name = "cafe")
public class Cafe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    /** Set when the cafe was picked from Google Maps; null for cafes typed in by hand. */
    @Column(name = "google_place_id", unique = true)
    private String googlePlaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null while pending; set when an admin approves the cafe. */
    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    protected Cafe() {
        // required by JPA
    }

    public Cafe(String name, String address, AppUser createdBy) {
        this(name, address, null, createdBy);
    }

    public Cafe(String name, String address, String googlePlaceId, AppUser createdBy) {
        this.name = name;
        this.address = address;
        this.googlePlaceId = googlePlaceId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isApproved() {
        return approvedAt != null;
    }

    public void approve(AppUser admin) {
        this.approvedAt = Instant.now();
        this.approvedBy = admin;
    }

    /**
     * Admins can delete any cafe. The creator can delete their own cafe only while it is pending;
     * once approved it becomes shared data that only admins manage.
     */
    public boolean canBeDeletedBy(AppUser user) {
        if (user.isAdmin()) {
            return true;
        }
        return !isApproved() && createdBy != null && createdBy.getId().equals(user.getId());
    }
}
