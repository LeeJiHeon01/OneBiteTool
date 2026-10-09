package com.example.aicook.adaptation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The request body carries allergy codes; it must not be logged here or in filters.
@RestController
@RequestMapping("/api/recipe-adaptations")
public class AdaptationController {

    private final AdaptationService adaptationService;

    public AdaptationController(AdaptationService adaptationService) {
        this.adaptationService = adaptationService;
    }

    @PostMapping
    public AdaptationResponse adapt(@RequestBody AdaptationRequest request) {
        return adaptationService.adapt(request);
    }
}
