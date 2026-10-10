package com.example.aicook.adaptation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    // A stored answer never holds allergy lists; allergies are not accepted here (the address is logged).
    @GetMapping("/{id}")
    public AdaptationResponse find(@PathVariable("id") String id) {
        return adaptationService.findStored(id);
    }
}
