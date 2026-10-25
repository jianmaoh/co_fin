package com.cofin.review;

import com.cofin.cafe.Cafe;
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
import java.time.LocalDate;

/** One user's review of one cafe; saving again edits it. */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cafe_id")
    private Cafe cafe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String content;

    @Column(name = "visited_on")
    private LocalDate visitedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {
        // required by JPA
    }

    public Review(Cafe cafe, AppUser user) {
        this.cafe = cafe;
        this.user = user;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(int rating, String content, LocalDate visitedOn) {
        this.rating = rating;
        this.content = content;
        this.visitedOn = visitedOn;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Cafe getCafe() {
        return cafe;
    }

    public AppUser getUser() {
        return user;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getVisitedOn() {
        return visitedOn;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
