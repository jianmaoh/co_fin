package com.cofin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CafeForm {

    @NotBlank(message = "請輸入店名")
    @Size(max = 100, message = "店名最多 100 個字")
    private String name;

    @NotBlank(message = "請輸入地址")
    @Size(max = 200, message = "地址最多 200 個字")
    private String address;

    /** Filled in by the Google Maps search box; empty when typed in by hand. */
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]{10,255}$", message = "Google 地點資料有誤，請重新搜尋")
    private String placeId;

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

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }
}
