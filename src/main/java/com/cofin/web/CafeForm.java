package com.cofin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CafeForm {

    @NotBlank(message = "請輸入店名")
    @Size(max = 100, message = "店名最多 100 個字")
    private String name;

    @Size(max = 200, message = "地址最多 200 個字")
    private String address;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
