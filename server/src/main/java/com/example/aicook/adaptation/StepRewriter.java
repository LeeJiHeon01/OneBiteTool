package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.ai.OpenAiClient.Purpose;
import com.example.aicook.common.Json;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.ToolNeeds;
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
 * Asks the AI to reword the finished steps, add a skill-level explanation and show a step
 * that holds several main actions as one step per action (prompts/recipe-adapt.md).
 * Only the wording and the split come from the AI: time, heat, the check, ingredients, tools and
 * safety notes are the server's and are placed by the server. A step whose answer fails
 * validation keeps the server's own sentence, unsplit. Allergy data is never sent.
 */
@Component
class StepRewriter {

    private static final Logger log = LoggerFactory.getLogger(StepRewriter.class);
    private static final String PROMPT = "recipe-adapt";
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_PARTS = 3;
    private static final Map<HeatLevel, String> HEAT_LABELS = Map.of(HeatLevel.LOW, "약불",
            HeatLevel.MEDIUM_LOW, "중약불", HeatLevel.MEDIUM, "중불", HeatLevel.MEDIUM_HIGH, "중강불",
            HeatLevel.HIGH, "강불");

    record Output(List<Rewrite> steps) {
    }

    record Rewrite(String stepId, List<Part> parts) {
    }

    /** actionKeys are the main actions the sentence holds; exactly one is allowed. */
    record Part(String action, List<String> actionKeys, String details) {

        String actionKey() {
            return actionKeys.get(0);
        }
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
        List<AdaptedStep> result = steps.stream()
                .flatMap(step -> Optional.ofNullable(accepted.get(step.stepId()))
                        .map(rewrite -> applied(step, rewrite.parts()))
                        .orElse(List.of(step)).stream())
                .toList();
        if (result.size() == steps.size()) {
            return result;
        }
        // A split step adds steps, so every step is numbered again.
        List<AdaptedStep> numbered = new ArrayList<>();
        for (AdaptedStep step : result) {
            numbered.add(step.order() == numbered.size() + 1 ? step : step.withOrder(numbered.size() + 1));
        }
        return numbered;
    }

    /** One step per accepted part; with a single part only the wording changes. */
    private static List<AdaptedStep> applied(AdaptedStep step, List<Part> parts) {
        if (parts.size() == 1) {
            return List.of(step.withText(parts.get(0).action(), parts.get(0).details()));
        }
        int anchor = anchorOf(step, parts);
        List<AdaptedStep> steps = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            boolean keepsTimer = index == anchor;
            // The source gave one heat level for the whole step: it also holds for its other heated actions.
            boolean keepsHeat = keepsTimer || ToolNeeds.isHeatingAction(part.actionKey());
            steps.add(step.partOf(index + 1, part.action(), part.actionKey(), part.details(), keepsTimer, keepsHeat));
        }
        return steps;
    }

    /**
     * The part that keeps the step's timer, device setting and check: the one with the step's own
     * action. Without such a part the last one keeps them, which is where the step ended.
     */
    private static int anchorOf(AdaptedStep step, List<Part> parts) {
        for (int index = 0; index < parts.size(); index++) {
            if (parts.get(index).actionKey().equals(step.actionKey())) {
                return index;
            }
        }
        return parts.size() - 1;
    }

    /** A step joined from several steps, or holding a checklist, is already one action and is not split. */
    private static boolean canSplit(AdaptedStep step) {
        return step.subItems().isEmpty() && step.mergedFromStepIds().size() <= 1;
    }

    private static boolean hasTimerOrHeat(AdaptedStep step) {
        return step.durationSeconds() != null || step.heatLevel() != null || step.deviceSettingRaw() != null;
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
        input.put("actionKey", step.actionKey());
        input.put("canSplit", canSplit(step));
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
            if (isValid(step, rewrite, allowedNames(step, ingredientsById))) {
                accepted.put(step.stepId(), rewrite);
            }
        }
        return accepted;
    }

    /**
     * Every part must be one main action in acceptable wording. A step with a timer, heat or a
     * device setting may only be split when exactly one part carries the step's own action, so
     * the server knows where those values belong.
     */
    private boolean isValid(AdaptedStep step, Rewrite rewrite, List<String> allowedNames) {
        List<Part> parts = rewrite.parts();
        if (parts == null || parts.isEmpty() || parts.size() > MAX_PARTS) {
            return false;
        }
        for (Part part : parts) {
            // Two main actions in one sentence break "one action per step".
            if (part.actionKeys() == null || part.actionKeys().size() != 1 || part.actionKey() == null
                    || !validator.accepts(step.action(), allowedNames, part.action(), part.details())) {
                return false;
            }
        }
        if (parts.size() == 1) {
            return true;
        }
        if (!canSplit(step)) {
            return false;
        }
        long anchors = parts.stream().filter(part -> part.actionKey().equals(step.actionKey())).count();
        return hasTimerOrHeat(step) ? anchors == 1 : anchors <= 1;
    }

    private static boolean sameSteps(List<AdaptedStep> steps, List<Rewrite> rewrites) {
        return steps.stream().map(AdaptedStep::stepId).toList()
                .equals(rewrites.stream().map(Rewrite::stepId).toList());
    }

    /** Experienced cooks never get the extra explanation; an empty one is the same as none. */
    private static Rewrite normalize(Rewrite rewrite, SkillLevel skillLevel) {
        if (rewrite.parts() == null) {
            return rewrite;
        }
        List<Part> parts = rewrite.parts().stream()
                .map(part -> {
                    String details = part.details();
                    if (skillLevel == SkillLevel.HIGH || details == null || details.isBlank()) {
                        details = null;
                    }
                    return new Part(part.action() == null ? null : part.action().strip(), part.actionKeys(), details);
                })
                .toList();
        return new Rewrite(rewrite.stepId(), parts);
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
