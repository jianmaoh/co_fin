package com.cofin.web;

import com.cofin.recommend.RecommendationService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RecommendationService recommendationService;

    public HomeController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/")
    public String home(Principal principal, Model model) {
        if (principal == null) {
            return "landing";
        }
        model.addAttribute("popular", recommendationService.popular());
        return "home";
    }
}
