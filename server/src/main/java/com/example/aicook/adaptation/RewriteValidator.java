package com.example.aicook.adaptation;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks a step text written by the AI (prompts/recipe-adapt.md). Amounts, times, heat and
 * safety wording belong to the server, so a text that mentions any of them is rejected.
 */
class RewriteValidator {

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final List<String> HEAT_WORDS = List.of("약불", "중약불", "중불", "중강불", "강불", "센 불", "센불",
            "약한 불", "약한불", "중간 불", "중간불", "불을 줄", "불을 낮", "불을 약", "불을 올", "불을 키", "불을 세");
    private static final List<String> SAFETY_WORDS = List.of("조심", "주의", "화상", "위험", "다치", "데지 않", "데이지 않",
            "베이지 않");

    /** Names of every ingredient and tool the server knows. */
    private final Set<String> knownNames;

    RewriteValidator(Set<String> knownNames) {
        this.knownNames = knownNames;
    }

    /**
     * @param originalAction the server's own sentence for the step
     * @param allowedNames   names of the ingredients and tools used in the step
     */
    boolean accepts(String originalAction, Collection<String> allowedNames, String action, String details) {
        if (action == null || action.isBlank()) {
            return false;
        }
        String text = details == null ? action : action + " " + details;
        if (DIGIT.matcher(text).find() || containsAny(text, HEAT_WORDS) || containsAny(text, SAFETY_WORDS)) {
            return false;
        }
        String allowedText = originalAction + " " + String.join(" ", allowedNames);
        return knownNames.stream().noneMatch(name -> text.contains(name) && !allowedText.contains(name));
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }
}
