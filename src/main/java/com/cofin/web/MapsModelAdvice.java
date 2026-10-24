package com.cofin.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the Google Maps browser key available to every page as {@code mapsApiKey}.
 * It is null when no key is configured, and pages then fall back to manual input.
 *
 * <p>A browser key is visible to anyone who opens the page; it is protected by the
 * website (HTTP referrer) and API restrictions set in Google Cloud, plus daily quotas.
 */
@ControllerAdvice
public class MapsModelAdvice {

    private final String apiKey;

    public MapsModelAdvice(@Value("${cofin.google-maps.api-key:}") String apiKey) {
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
    }

    @ModelAttribute("mapsApiKey")
    public String mapsApiKey() {
        return apiKey;
    }
}
