package com.cofin.web;

import com.cofin.collection.CollectionService;
import com.cofin.common.CurrentUser;
import com.cofin.common.InvalidInputException;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CollectionController {

    private final CollectionService collectionService;
    private final CurrentUser currentUser;

    public CollectionController(CollectionService collectionService, CurrentUser currentUser) {
        this.collectionService = collectionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/collections")
    public String list(Principal principal, Model model) {
        model.addAttribute("collections", collectionService.listFor(currentUser.get(principal).getId()));
        return "collections/list";
    }

    @PostMapping("/collections")
    public String create(@RequestParam String name, Principal principal, RedirectAttributes redirect) {
        try {
            Long id = collectionService.create(currentUser.get(principal).getId(), name);
            redirect.addFlashAttribute("message", "已建立清單");
            return "redirect:/collections/" + id;
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/collections";
        }
    }

    @GetMapping("/collections/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("collection", collectionService.detail(currentUser.get(principal).getId(), id));
        return "collections/detail";
    }

    @PostMapping("/collections/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name,
                         Principal principal, RedirectAttributes redirect) {
        try {
            collectionService.rename(currentUser.get(principal).getId(), id, name);
            redirect.addFlashAttribute("message", "已更新名稱");
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/collections/" + id;
    }

    @PostMapping("/collections/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        collectionService.delete(currentUser.get(principal).getId(), id);
        redirect.addFlashAttribute("message", "已刪除清單");
        return "redirect:/collections";
    }

    /** Submitted from a cafe page, which is where the user goes back to. */
    @PostMapping("/collections/items")
    public String addItem(@RequestParam Long collectionId, @RequestParam Long cafeId,
                          @RequestParam(required = false) String note,
                          Principal principal, RedirectAttributes redirect) {
        try {
            boolean added = collectionService.addItem(currentUser.get(principal).getId(), collectionId, cafeId, note);
            redirect.addFlashAttribute("message", added ? "已加入清單" : "這家店已經在清單裡了");
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cafes/" + cafeId;
    }

    @PostMapping("/collections/{id}/items/{cafeId}/delete")
    public String removeItem(@PathVariable Long id, @PathVariable Long cafeId,
                             Principal principal, RedirectAttributes redirect) {
        collectionService.removeItem(currentUser.get(principal).getId(), id, cafeId);
        redirect.addFlashAttribute("message", "已從清單移除");
        return "redirect:/collections/" + id;
    }
}
