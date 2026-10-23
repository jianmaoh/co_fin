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

/** One user's vote that a cafe has a tag. */
@Entity
@Table(name = "cafe_tag")
public class CafeTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cafe_id")
    private Cafe cafe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CafeTag() {
        // required by JPA
    }

    public CafeTag(Cafe cafe, Tag tag, AppUser user) {
        this.cafe = cafe;
        this.tag = tag;
        this.user = user;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Cafe getCafe() {
        return cafe;
    }

    public Tag getTag() {
        return tag;
    }

    public AppUser getUser() {
        return user;
    }
}
