package com.cofin.web;

import com.cofin.cafe.CafeService;
import com.cofin.common.CurrentUser;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin-only (enforced in SecurityConfig for /admin/**, and again in CafeService). */
@Controller
public class AdminController {

    private final CafeService cafeService;
    private final CurrentUser currentUser;

    public AdminController(CafeService cafeService, CurrentUser currentUser) {
        this.cafeService = cafeService;
        this.currentUser = currentUser;
    }

    @GetMapping("/admin/cafes")
    public String pending(Model model) {
        model.addAttribute("pending", cafeService.pending());
        return "admin/cafes";
    }

    @PostMapping("/admin/cafes/{id}/approve")
    public String approve(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        cafeService.approve(id, currentUser.get(principal));
        redirect.addFlashAttribute("message", "已核准");
        return "redirect:/admin/cafes";
    }

    @PostMapping("/admin/cafes/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        cafeService.delete(id, currentUser.get(principal));
        redirect.addFlashAttribute("message", "已刪除");
        return "redirect:/admin/cafes";
    }
}
