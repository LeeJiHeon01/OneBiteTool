package com.example.aicook.adaptation;

import com.example.aicook.common.ApiException;
import com.example.aicook.common.Json;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Heat pictures that exist (assets/source/images/manifest.json). A step is only answered with a
 * heat level whose picture is prepared: the picture and the label must always show the same
 * level, so a level without a picture fails instead of being shown with a placeholder.
 */
final class HeatVisuals {

    private static final String READY = "ready";
    private static final Set<String> READY_KEYS = Json.readResource("images/manifest.json", Manifest.class).images()
            .stream()
            .filter(image -> READY.equals(image.status()))
            .map(Image::key)
            .collect(Collectors.toUnmodifiableSet());

    private HeatVisuals() {
    }

    record Manifest(List<Image> images) {
    }

    record Image(String key, String status) {
    }

    /** Returns the key when its picture is prepared (null stays null: a step without heat). */
    static String requireKnown(String heatVisualKey) {
        return requireKnown(heatVisualKey, READY_KEYS);
    }

    static String requireKnown(String heatVisualKey, Set<String> readyKeys) {
        if (heatVisualKey != null && !readyKeys.contains(heatVisualKey)) {
            // The key names a heat level and a stove type, nothing about the user.
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UNKNOWN_HEAT_VISUAL",
                    "이 단계의 불 세기를 보여줄 수 없어요. 잠시 후 다시 시도해 주세요.");
        }
        return heatVisualKey;
    }

    static boolean isReady(String heatVisualKey) {
        return READY_KEYS.contains(heatVisualKey);
    }
}
