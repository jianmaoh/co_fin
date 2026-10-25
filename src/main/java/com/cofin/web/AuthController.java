package com.cofin.web;

import com.cofin.user.UserService;
import com.cofin.user.UsernameTakenException;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(Principal principal) {
        return principal != null ? "redirect:/" : "login";
    }

    @GetMapping("/register")
    public String registerForm(@ModelAttribute("form") RegisterForm form, Principal principal) {
        return principal != null ? "redirect:/" : "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "兩次輸入的密碼不一致");
        }
        if (result.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form.getUsername(), form.getPassword());
        } catch (UsernameTakenException e) {
            result.rejectValue("username", "taken", "這個帳號已經有人使用");
            return "register";
        }
        return "redirect:/login?registered";
    }
}
