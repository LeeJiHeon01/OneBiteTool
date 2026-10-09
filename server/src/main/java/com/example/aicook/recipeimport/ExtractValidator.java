package com.example.aicook.recipeimport;

import com.example.aicook.reference.ReferenceData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks an extracted recipe against the page it came from. Every amount, time, heat level and
 * device setting must be quoted from the page; an extract with anything else is rejected whole,
 * so nothing the model made up is ever stored.
 */
class ExtractValidator {

    // a number and the word right after it: "1cm", "2 큰술", "1/2개"
    private static final Pattern NUMBER_AND_WORD = Pattern.compile("(\\d+(?:[./]\\d+)?)\\s*([^\\d\\s,.()]*)");
    /** A shorter match ("물을 넣는다") is too common a phrase to count as copying. */
    private static final int MIN_COPIED_LENGTH = 12;
    private static final List<String> TIME_AND_TEMPERATURE_UNITS = List.of("시간", "분", "초", "도", "℃", "°", "W");
    private static final Pattern ACTION_KEY = Pattern.compile("action\\.[a-z_]+");
    // "3분", "2~3분", "1시간": for a range the first (shorter) number is the one that counts.
    private static final Pattern DURATION = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:[~\\-–]\\s*\\d+(?:\\.\\d+)?\\s*)?(시간|분|초)");

    private final ReferenceData reference;
    /** Units of amounts, times and temperatures; a number with one of these may not be in a step sentence. */
    private final List<String> measuredUnits;

    ExtractValidator(ReferenceData reference) {
        this.reference = reference;
        this.measuredUnits = new ArrayList<>(TIME_AND_TEMPERATURE_UNITS);
        measuredUnits.addAll(reference.units().mlByUnit().keySet());
        measuredUnits.addAll(reference.units().weightUnits());
    }

    /** @return the first rule the extract breaks (a short code for logs), or empty when it is valid */
    Optional<String> firstViolation(Extract.Page page, String pageText) {
        if (!page.isRecipe() || isEmpty(page.ingredients()) || isEmpty(page.steps())) {
            return Optional.of("NOT_A_RECIPE");
        }
        String source = compact(pageText);
        for (Extract.Ingredient ingredient : page.ingredients()) {
            if (isBlank(ingredient.name()) || !quoted(ingredient.evidence(), source)) {
                return Optional.of("INGREDIENT_NOT_IN_PAGE");
            }
            String evidence = compact(ingredient.evidence());
            if (!evidence.contains(compact(ingredient.name()))) {
                return Optional.of("INGREDIENT_NOT_IN_PAGE");
            }
            if (ingredient.amountText() != null && !evidence.contains(compact(ingredient.amountText()))) {
                return Optional.of("AMOUNT_NOT_IN_PAGE");
            }
        }
        Set<String> ingredientNames = page.ingredients().stream()
                .map(Extract.Ingredient::name)
                .collect(Collectors.toSet());
        for (Extract.Step step : page.steps()) {
            Optional<String> violation = stepViolation(step, source, ingredientNames);
            if (violation.isPresent()) {
                return violation;
            }
        }
        return Optional.empty();
    }

    private Optional<String> stepViolation(Extract.Step step, String source, Set<String> ingredientNames) {
        if (isBlank(step.action()) || !quoted(step.evidence(), source)) {
            return Optional.of("STEP_NOT_IN_PAGE");
        }
        // Only reworded sentences are stored; a sentence lifted from the page is not ours to keep.
        String sentence = compact(step.action()).replaceAll("[.!。]+$", "");
        if (sentence.length() >= MIN_COPIED_LENGTH && source.contains(sentence)) {
            return Optional.of("ACTION_COPIED");
        }
        Optional<String> numberViolation = numberViolation(step);
        if (numberViolation.isPresent()) {
            return numberViolation;
        }
        if (step.durationSeconds() != null && !durationQuoted(step, source)) {
            return Optional.of("TIME_NOT_IN_PAGE");
        }
        if (step.heatLevel() != null && !quoted(step.heatRaw(), source)) {
            return Optional.of("HEAT_NOT_IN_PAGE");
        }
        if (step.deviceSettingRaw() != null && (step.heatLevel() != null || !quoted(step.deviceSettingRaw(), source))) {
            return Optional.of("DEVICE_SETTING_NOT_IN_PAGE");
        }
        if (step.ingredientNames() == null || !ingredientNames.containsAll(step.ingredientNames())) {
            return Optional.of("UNKNOWN_STEP_INGREDIENT");
        }
        if (step.toolCodes() == null || step.toolCodes().stream().anyMatch(code -> reference.tool(code).isEmpty())) {
            return Optional.of("UNKNOWN_TOOL");
        }
        if (step.actionKey() != null && !ACTION_KEY.matcher(step.actionKey()).matches()) {
            return Optional.of("UNKNOWN_ACTION_KEY");
        }
        return Optional.empty();
    }

    /**
     * A number in the rewritten sentence must come from the quoted source ("1cm 두께로"), and it
     * must not be an amount, time or temperature: those are shown from the checked fields and the
     * amount may be converted to the user's tools, so a copy in the sentence would contradict it.
     */
    private Optional<String> numberViolation(Extract.Step step) {
        String evidence = compact(step.evidence());
        Matcher matcher = NUMBER_AND_WORD.matcher(step.action());
        while (matcher.find()) {
            if (!evidence.contains(matcher.group(1))) {
                return Optional.of("NUMBER_NOT_IN_PAGE");
            }
            String word = matcher.group(2);
            if (measuredUnits.stream().anyMatch(word::startsWith)) {
                return Optional.of("AMOUNT_IN_ACTION");
            }
        }
        return Optional.empty();
    }

    /** The quoted phrase must be on the page and its numbers must add up to the stated seconds. */
    private static boolean durationQuoted(Extract.Step step, String source) {
        if (step.durationSeconds() <= 0 || !quoted(step.durationEvidence(), source)) {
            return false;
        }
        Matcher matcher = DURATION.matcher(step.durationEvidence());
        double seconds = 0;
        while (matcher.find()) {
            double unitSeconds = switch (matcher.group(2)) {
                case "시간" -> 3600;
                case "분" -> 60;
                default -> 1;
            };
            seconds += Double.parseDouble(matcher.group(1)) * unitSeconds;
        }
        return Math.round(seconds) == step.durationSeconds();
    }

    /** The servings count is kept only when its quote is on the page and names the number. */
    static boolean servingsQuoted(Extract.Page page, String pageText) {
        if (page.servings() == null || page.servings() <= 0 || !quoted(page.servingsEvidence(), compact(pageText))) {
            return false;
        }
        String number = page.servings() % 1 == 0
                ? String.valueOf(page.servings().intValue()) : String.valueOf(page.servings());
        return page.servingsEvidence().contains(number);
    }

    private static boolean quoted(String evidence, String compactSource) {
        return !isBlank(evidence) && compactSource.contains(compact(evidence));
    }

    /** Whitespace differs between the page and the quote, so both are compared without it. */
    static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
