package com.cofin.review;

import com.cofin.cafe.Cafe;
import com.cofin.cafe.CafeRepository;
import com.cofin.cafe.CafeTagRepository;
import com.cofin.cafe.UserTag;
import com.cofin.common.InvalidInputException;
import com.cofin.common.Names;
import com.cofin.common.NotFoundException;
import com.cofin.user.AppUserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    static final int CONTENT_MAX_LENGTH = 1000;
    static final int MAX_PHOTOS = 3;
    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    /** "Today" for the not-in-the-future check, independent of the server's time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    private final ReviewRepository reviews;
    private final ReviewPhotoRepository photos;
    private final CafeRepository cafes;
    private final CafeTagRepository cafeTags;
    private final AppUserRepository users;

    public ReviewService(ReviewRepository reviews, ReviewPhotoRepository photos, CafeRepository cafes,
                         CafeTagRepository cafeTags, AppUserRepository users) {
        this.reviews = reviews;
        this.photos = photos;
        this.cafes = cafes;
        this.cafeTags = cafeTags;
        this.users = users;
    }

    /** Everyone who reviewed or tagged the cafe: reviewers first (most recent first), then tag-only users. */
    public List<Participant> participants(Long cafeId) {
        List<ReviewView> views = toViews(reviews.findByCafeWithUser(cafeId));
        Map<String, List<String>> tagsByUser = cafeTags.findUserTags(cafeId).stream()
                .collect(Collectors.groupingBy(UserTag::username, LinkedHashMap::new,
                        Collectors.mapping(UserTag::tag, Collectors.toList())));

        List<Participant> result = new ArrayList<>();
        for (ReviewView view : views) {
            result.add(new Participant(view.username(), view, tagsByUser.getOrDefault(view.username(), List.of())));
        }
        Map<String, ReviewView> byUser = views.stream().collect(Collectors.toMap(ReviewView::username, Function.identity()));
        tagsByUser.forEach((username, tags) -> {
            if (!byUser.containsKey(username)) {
                result.add(new Participant(username, null, tags));
            }
        });
        return result;
    }

    public Optional<ReviewView> myReview(Long cafeId, Long userId) {
        return reviews.findByCafeIdAndUserId(cafeId, userId).map(r -> toViews(List.of(r)).getFirst());
    }

    public RatingSummary ratingFor(Long cafeId) {
        return ratingsFor(List.of(cafeId)).getOrDefault(cafeId, RatingSummary.none(cafeId));
    }

    public Map<Long, RatingSummary> ratingsFor(Collection<Long> cafeIds) {
        if (cafeIds.isEmpty()) {
            return Map.of();
        }
        return reviews.summarize(cafeIds).stream().collect(Collectors.toMap(RatingSummary::cafeId, Function.identity()));
    }

    /**
     * Creates or edits the caller's review. Photos in {@code deletePhotoIds} are removed first
     * (only the caller's own), then {@code newPhotos} are added, up to {@value #MAX_PHOTOS} in total.
     */
    @Transactional
    public void save(Long cafeId, Long userId, Integer rating, String rawContent, LocalDate visitedOn,
                     List<MultipartFile> newPhotos, List<Long> deletePhotoIds) {
        Cafe cafe = cafes.findById(cafeId).orElseThrow(() -> new NotFoundException("找不到這家咖啡廳"));
        if (rating == null || rating < 1 || rating > 5) {
            throw new InvalidInputException("請選擇 1 到 5 顆星");
        }
        String content = Names.optional(rawContent, CONTENT_MAX_LENGTH, "評論");
        if (visitedOn != null && visitedOn.isAfter(LocalDate.now(ZONE))) {
            throw new InvalidInputException("造訪日期不能是未來");
        }
        List<byte[]> uploads = readPhotos(newPhotos);

        Review review = reviews.findByCafeIdAndUserId(cafeId, userId)
                .orElseGet(() -> new Review(cafe, users.getReferenceById(userId)));
        // Fill in the fields before the first save: identity ids make Hibernate insert immediately,
        // and the database rejects a rating outside 1..5.
        review.update(rating, content, visitedOn);
        if (review.getId() == null) {
            review = reviews.save(review);
        }

        if (deletePhotoIds != null && !deletePhotoIds.isEmpty()) {
            // Scoped to this review, so ids belonging to other people's reviews are ignored.
            photos.deleteFromReview(review.getId(), deletePhotoIds);
        }
        if (photos.countByReviewId(review.getId()) + uploads.size() > MAX_PHOTOS) {
            throw new InvalidInputException("每則評論最多 " + MAX_PHOTOS + " 張照片");
        }
        for (byte[] data : uploads) {
            String type = ImageType.detect(data).orElseThrow();
            photos.save(new ReviewPhoto(review, type, data));
        }
    }

    @Transactional
    public void delete(Long cafeId, Long userId) {
        reviews.findByCafeIdAndUserId(cafeId, userId).ifPresent(reviews::delete);
    }

    public PhotoContent photo(Long photoId) {
        return photos.findContent(photoId).orElseThrow(() -> new NotFoundException("找不到照片"));
    }

    private static List<byte[]> readPhotos(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        List<byte[]> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue; // a file input with nothing selected still sends an empty part
            }
            if (file.getSize() > MAX_PHOTO_BYTES) {
                throw new InvalidInputException("每張照片最大 5 MB");
            }
            byte[] data;
            try {
                data = file.getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (ImageType.detect(data).isEmpty()) {
                throw new InvalidInputException("只接受 JPEG、PNG 或 WebP 照片");
            }
            result.add(data);
        }
        if (result.size() > MAX_PHOTOS) {
            throw new InvalidInputException("每則評論最多 " + MAX_PHOTOS + " 張照片");
        }
        return result;
    }

    private List<ReviewView> toViews(List<Review> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> photoIds = photos.findRefs(list.stream().map(Review::getId).toList()).stream()
                .collect(Collectors.groupingBy(PhotoRef::reviewId, Collectors.mapping(PhotoRef::id, Collectors.toList())));
        return list.stream()
                .map(r -> new ReviewView(r.getId(), r.getUser().getUsername(), r.getRating(), r.getContent(),
                        r.getVisitedOn(), r.getUpdatedAt(), photoIds.getOrDefault(r.getId(), List.of())))
                .toList();
    }
}
