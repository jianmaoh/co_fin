package com.cofin.web;

import com.cofin.cafe.CafeService;
import com.cofin.cafe.PlaceMatch;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON used by the home page: which nearby Google places are already in Co-Fin. */
@RestController
public class CafeLookupController {

    static final int MAX_IDS = 20;
    private static final Pattern PLACE_ID = Pattern.compile("^[A-Za-z0-9_-]{10,255}$");

    private final CafeService cafeService;

    public CafeLookupController(CafeService cafeService) {
        this.cafeService = cafeService;
    }

    @GetMapping("/api/cafes/by-place")
    public List<PlaceMatch> byPlace(@RequestParam(name = "ids", defaultValue = "") List<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        if (unique.size() > MAX_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_IDS + " ids");
        }
        if (!unique.stream().allMatch(id -> PLACE_ID.matcher(id).matches())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed place id");
        }
        return cafeService.findByPlaceIds(unique);
    }
}
