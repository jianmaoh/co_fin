package com.cofin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterForm {

    @NotBlank(message = "請輸入帳號")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "帳號需為 3–20 個英文字母、數字或底線")
    private String username;

    @NotBlank(message = "請輸入密碼")
    @Size(min = 8, max = 72, message = "密碼長度需為 8–72 個字元")
    private String password;

    @NotBlank(message = "請再輸入一次密碼")
    private String confirmPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
