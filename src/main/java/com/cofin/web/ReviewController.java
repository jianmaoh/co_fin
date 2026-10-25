package com.cofin.web;

import com.cofin.common.CurrentUser;
import com.cofin.common.InvalidInputException;
import com.cofin.review.PhotoContent;
import com.cofin.review.ReviewService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @PostMapping("/cafes/{id}/review")
    public String save(@PathVariable Long id,
                       @RequestParam(required = false) Integer rating,
                       @RequestParam(required = false) String content,
                       @RequestParam(required = false) String visitedOn,
                       @RequestParam(name = "photos", required = false) List<MultipartFile> photos,
                       @RequestParam(name = "deletePhotoIds", required = false) List<Long> deletePhotoIds,
                       Principal principal, RedirectAttributes redirect) {
        try {
            reviewService.save(id, currentUser.get(principal).getId(), rating, content, parseDate(visitedOn),
                    photos, deletePhotoIds);
            redirect.addFlashAttribute("message", "已儲存你的評論");
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cafes/" + id + "#my-review";
    }

    @PostMapping("/cafes/{id}/review/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        reviewService.delete(id, currentUser.get(principal).getId());
        redirect.addFlashAttribute("message", "已刪除你的評論");
        return "redirect:/cafes/" + id;
    }

    /** The stored type was detected from the bytes on upload, so it is safe to send as Content-Type. */
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        PhotoContent photo = reviewService.photo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(photo.data());
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("造訪日期格式不正確");
        }
    }
}
