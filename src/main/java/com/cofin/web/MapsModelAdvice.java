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
    private final String mapId;

    public MapsModelAdvice(@Value("${cofin.google-maps.api-key:}") String apiKey,
                           @Value("${cofin.google-maps.map-id:}") String mapId) {
        this.apiKey = blankToNull(apiKey);
        this.mapId = blankToNull(mapId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @ModelAttribute("mapsApiKey")
    public String mapsApiKey() {
        return apiKey;
    }

    /** A Map ID with a custom style from Google Cloud; null means "use the built-in dark theme". */
    @ModelAttribute("mapsMapId")
    public String mapsMapId() {
        return mapId;
    }
}
