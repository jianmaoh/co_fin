package com.cofin.web;

import com.cofin.cafe.CafeService;
import com.cofin.cafe.CreateResult;
import com.cofin.cafe.SearchResult;
import com.cofin.collection.CollectionService;
import com.cofin.common.CurrentUser;
import com.cofin.common.InvalidInputException;
import com.cofin.review.ReviewService;
import com.cofin.user.AppUser;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CafeController {

    private final CafeService cafeService;
    private final CollectionService collectionService;
    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public CafeController(CafeService cafeService, CollectionService collectionService,
                          ReviewService reviewService, CurrentUser currentUser) {
        this.cafeService = cafeService;
        this.collectionService = collectionService;
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        List<SearchResult> results = cafeService.search(q);
        model.addAttribute("q", q);
        model.addAttribute("results", results);
        model.addAttribute("ratings", reviewService.ratingsFor(results.stream().map(SearchResult::id).toList()));
        model.addAttribute("popularTags", cafeService.popularTags());
        return "search";
    }

    /** The old list page; kept so existing links and bookmarks still work. */
    @GetMapping("/cafes")
    public String cafes(@RequestParam(defaultValue = "") String q) {
        return q.isBlank() ? "redirect:/search" : "redirect:/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
    }

    @GetMapping("/cafes/new")
    public String newForm(@ModelAttribute("form") CafeForm form) {
        return "cafes/new";
    }

    @PostMapping("/cafes")
    public String create(@Valid @ModelAttribute("form") CafeForm form, BindingResult result,
                         Principal principal, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "cafes/new";
        }
        AppUser me = currentUser.get(principal);
        try {
            CreateResult created = cafeService.create(form.getName(), form.getAddress(), form.getPlaceId(), me.getId());
            redirect.addFlashAttribute("message", created.created() ? "已新增咖啡廳" : "這家咖啡廳已經有人新增過了");
            return "redirect:/cafes/" + created.id();
        } catch (InvalidInputException e) {
            result.reject("invalid", e.getMessage());
            return "cafes/new";
        }
    }

    @GetMapping("/cafes/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        AppUser me = currentUser.get(principal);
        model.addAttribute("cafe", cafeService.detail(id, me.getId()));
        model.addAttribute("myCollections", collectionService.listFor(me.getId()));
        model.addAttribute("savedIn", collectionService.collectionsContaining(me.getId(), id));
        model.addAttribute("rating", reviewService.ratingFor(id));
        model.addAttribute("participants", reviewService.participants(id));
        model.addAttribute("myReview", reviewService.myReview(id, me.getId()).orElse(null));
        model.addAttribute("today", LocalDate.now(ZoneId.of("Asia/Taipei")));
        return "cafes/detail";
    }

    @PostMapping("/cafes/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        cafeService.delete(id, currentUser.get(principal));
        redirect.addFlashAttribute("message", "已刪除咖啡廳");
        return "redirect:/search";
    }

    @PostMapping("/cafes/{id}/approve")
    public String approve(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        cafeService.approve(id, currentUser.get(principal));
        redirect.addFlashAttribute("message", "已核准這家咖啡廳");
        return "redirect:/cafes/" + id;
    }

    @PostMapping("/cafes/{id}/tags")
    public String addTag(@PathVariable Long id, @RequestParam String name,
                         Principal principal, RedirectAttributes redirect) {
        try {
            cafeService.addTag(id, currentUser.get(principal).getId(), name);
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cafes/" + id;
    }

    @PostMapping("/cafes/{id}/tags/remove")
    public String removeTag(@PathVariable Long id, @RequestParam String name,
                            Principal principal, RedirectAttributes redirect) {
        try {
            cafeService.removeTag(id, currentUser.get(principal).getId(), name);
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cafes/" + id;
    }
}
