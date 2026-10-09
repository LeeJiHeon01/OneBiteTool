package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.ai.OpenAiClient.Purpose;
import com.example.aicook.common.Json;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Tools.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asks the AI to reword the finished steps and add a skill-level explanation
 * (prompts/recipe-adapt.md). Only {@code action} and {@code details} can change; a text that
 * fails validation is replaced by the server's own sentence. Allergy data is never sent.
 */
@Component
class StepRewriter {

    private static final Logger log = LoggerFactory.getLogger(StepRewriter.class);
    private static final String PROMPT = "recipe-adapt";
    private static final int MAX_ATTEMPTS = 2;
    private static final Map<HeatLevel, String> HEAT_LABELS = Map.of(HeatLevel.LOW, "약불",
            HeatLevel.MEDIUM_LOW, "중약불", HeatLevel.MEDIUM, "중불", HeatLevel.MEDIUM_HIGH, "중강불",
            HeatLevel.HIGH, "강불");

    record Output(List<Rewrite> steps) {
    }

    record Rewrite(String stepId, String action, String details) {
    }

    private final OpenAiClient client;
    private final ReferenceData reference;
    private final RewriteValidator validator;

    StepRewriter(OpenAiClient client, ReferenceData reference) {
        this.client = client;
        this.reference = reference;
        this.validator = new RewriteValidator(reference.ingredientAndToolNames());
    }

    List<AdaptedStep> rewrite(SkillLevel skillLevel, List<AdaptedStep> steps, List<AdaptedIngredient> ingredients,
                              List<Change> changes) {
        if (steps.isEmpty() || !client.isAvailable()) {
            return steps;
        }
        Map<String, AdaptedIngredient> ingredientsById = ingredients.stream()
                .collect(Collectors.toMap(AdaptedIngredient::ingredientId, Function.identity()));
        Map<String, Object> input = input(skillLevel, steps, ingredientsById, changes);

        Map<String, Rewrite> accepted = new HashMap<>();
        for (int attempt = 0; attempt < MAX_ATTEMPTS && accepted.size() < steps.size(); attempt++) {
            Optional<String> answer = client.complete(Purpose.ADAPT, PROMPT, input);
            if (answer.isEmpty()) {
                break;
            }
            accepted.putAll(acceptedRewrites(answer.get(), skillLevel, steps, ingredientsById));
        }
        log.info("AI rewrite accepted for {} of {} steps", accepted.size(), steps.size());
        return steps.stream()
                .map(step -> Optional.ofNullable(accepted.get(step.stepId()))
                        .map(rewrite -> step.withText(rewrite.action(), rewrite.details()))
                        .orElse(step))
                .toList();
    }

    private Map<String, Object> input(SkillLevel skillLevel, List<AdaptedStep> steps,
                                      Map<String, AdaptedIngredient> ingredientsById, List<Change> changes) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("skillLevel", skillLevel.name());
        input.put("steps", steps.stream().map(step -> stepInput(step, ingredientsById)).toList());
        input.put("changes", changes.stream().map(Change::description).toList());
        return input;
    }

    private Map<String, Object> stepInput(AdaptedStep step, Map<String, AdaptedIngredient> ingredientsById) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("stepId", step.stepId());
        input.put("action", step.action());
        input.put("ingredients", step.ingredientIds().stream()
                .map(ingredientsById::get)
                .map(ingredient -> Map.of("name", ingredient.name(), "amountText", ingredient.display().text()))
                .toList());
        input.put("tools", toolNames(step));
        input.put("heatLabel", step.heatLevel() == null ? null : HEAT_LABELS.get(step.heatLevel()));
        input.put("durationSeconds", step.durationSeconds());
        input.put("completionCheck", step.completionCheck());
        return input;
    }

    /** Rewrites that pass validation. An answer with other steps than the input is dropped whole. */
    private Map<String, Rewrite> acceptedRewrites(String answer, SkillLevel skillLevel, List<AdaptedStep> steps,
                                                  Map<String, AdaptedIngredient> ingredientsById) {
        List<Rewrite> rewrites;
        try {
            rewrites = Json.parse(answer, Output.class).steps();
        } catch (RuntimeException exception) {
            return Map.of();
        }
        if (rewrites == null || !sameSteps(steps, rewrites)) {
            return Map.of();
        }
        Map<String, Rewrite> accepted = new HashMap<>();
        for (int index = 0; index < steps.size(); index++) {
            AdaptedStep step = steps.get(index);
            Rewrite rewrite = normalize(rewrites.get(index), skillLevel);
            if (validator.accepts(step.action(), allowedNames(step, ingredientsById), rewrite.action(),
                    rewrite.details())) {
                accepted.put(step.stepId(), rewrite);
            }
        }
        return accepted;
    }

    private static boolean sameSteps(List<AdaptedStep> steps, List<Rewrite> rewrites) {
        return steps.stream().map(AdaptedStep::stepId).toList()
                .equals(rewrites.stream().map(Rewrite::stepId).toList());
    }

    /** Experienced cooks never get the extra explanation; an empty one is the same as none. */
    private static Rewrite normalize(Rewrite rewrite, SkillLevel skillLevel) {
        String details = rewrite.details();
        if (skillLevel == SkillLevel.HIGH || details == null || details.isBlank()) {
            details = null;
        }
        return new Rewrite(rewrite.stepId(), rewrite.action() == null ? null : rewrite.action().strip(), details);
    }

    private List<String> allowedNames(AdaptedStep step, Map<String, AdaptedIngredient> ingredientsById) {
        List<String> names = new ArrayList<>(toolNames(step));
        step.ingredientIds().forEach(id -> names.add(ingredientsById.get(id).name()));
        return names;
    }

    private List<String> toolNames(AdaptedStep step) {
        return step.toolIds().stream()
                .map(reference::tool)
                .flatMap(Optional::stream)
                .map(Variant::name)
                .toList();
    }
}
