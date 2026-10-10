package com.example.aicook.recipe;

import com.example.aicook.common.Json;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Tools.Category;
import com.example.aicook.reference.Tools.Variant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which tools a recipe needs: the tools its steps name, the tools its actions imply
 * (data/seed/action-tools.json; web recipes often do not name tools) and a burner for heat.
 */
public final class ToolNeeds {

    public static final String STOVE = "stove";
    private static final Set<String> STOVE_TYPES = Set.of("GAS", "INDUCTION");
    /** Tool category of heating appliances; a step cooked in one needs no burner. */
    private static final String APPLIANCE_CATEGORY = "heat_source";
    private static final List<ActionRule> ACTION_RULES =
            Json.readResource("seed/action-tools.json", ActionRules.class).rules();

    private ToolNeeds() {
    }

    /** A need is met by owning any one of {@code anyOf}. */
    public record Need(String id, String label, List<String> anyOf, String reason) {
    }

    record ActionRules(List<ActionRule> rules) {
    }

    /** skipCategories: the rule does not apply to a step that already uses a tool of these categories. */
    record ActionRule(String id, List<String> actionKeys, List<String> anyOf, String label, String reason,
                      boolean needsStove, List<String> skipCategories) {

        boolean isSingleTool() {
            return anyOf.size() == 1;
        }

        boolean appliesTo(Set<String> usedCategories) {
            return skipCategories.stream().noneMatch(usedCategories::contains);
        }
    }

    /**
     * Tools of one step: the ones the recipe names plus a tool its action implies when there is
     * exactly one choice (cutting needs a knife). Substitution rules and safety notes apply to these.
     */
    public static List<String> stepTools(Recipe.Step step, ReferenceData reference) {
        Set<String> tools = new LinkedHashSet<>(step.toolIds());
        Set<String> usedCategories = categoriesOf(step.toolIds(), reference);
        rulesFor(step).stream()
                .filter(rule -> rule.isSingleTool() && rule.appliesTo(usedCategories))
                .forEach(rule -> tools.addAll(rule.anyOf()));
        return List.copyOf(tools);
    }

    /** True for actions done on a burner (달구기, 볶기, 끓이기, 굽기), as listed in action-tools.json. */
    public static boolean isHeatingAction(String actionKey) {
        return actionKey != null && ACTION_RULES.stream()
                .anyMatch(rule -> rule.needsStove() && rule.actionKeys().contains(actionKey));
    }

    /** True when one of the tools is a heating appliance (microwave, air fryer, oven, rice cooker). */
    public static boolean usesAppliance(List<String> toolIds, ReferenceData reference) {
        return categoriesOf(toolIds, reference).contains(APPLIANCE_CATEGORY);
    }

    public static List<Need> of(Recipe recipe, ReferenceData reference) {
        // tool id -> first step that uses it
        Map<String, Integer> firstUse = new LinkedHashMap<>();
        Map<String, Need> choices = new LinkedHashMap<>();
        boolean needsStove = false;
        for (Recipe.Step step : recipe.steps()) {
            stepTools(step, reference).forEach(toolId -> firstUse.putIfAbsent(toolId, step.order()));
            Set<String> usedCategories = categoriesOf(step.toolIds(), reference);
            for (ActionRule rule : rulesFor(step)) {
                // A named pan still sits on a burner; only an appliance step needs none.
                needsStove |= rule.needsStove() && !usedCategories.contains(APPLIANCE_CATEGORY);
                if (!rule.isSingleTool() && rule.appliesTo(usedCategories)) {
                    choices.putIfAbsent(rule.id(), new Need(rule.id(), rule.label(), rule.anyOf(), rule.reason()));
                }
            }
            // A step with a heat level is cooked on a burner: gas or induction.
            needsStove |= step.usesHeat();
        }
        List<Need> needs = new ArrayList<>();
        firstUse.forEach((toolId, order) -> needs.add(new Need(toolId,
                reference.tool(toolId).map(Variant::name).orElse(toolId), List.of(toolId),
                order + "단계에 필요해요")));
        // A choice is already settled when the recipe names one of its tools ("냄비에 육수를 붓는다").
        choices.values().stream()
                .filter(choice -> choice.anyOf().stream().noneMatch(firstUse::containsKey))
                .forEach(needs::add);
        if (needsStove) {
            List<Variant> stoves = reference.tools().stream()
                    .filter(tool -> tool.heatSourceType() != null && STOVE_TYPES.contains(tool.heatSourceType()))
                    .toList();
            needs.add(new Need(STOVE, String.join(" 또는 ", stoves.stream().map(Variant::name).toList()),
                    stoves.stream().map(Variant::id).toList(), "불을 쓰는 단계가 있어요"));
        }
        return needs;
    }

    /**
     * Needs the user's tools do not cover, not even through a verified substitution rule
     * (scissors for a knife). The same decision the adaptation makes for its required tools.
     */
    public static List<Need> missing(Recipe recipe, ReferenceData reference, Set<String> ownedToolIds) {
        return of(recipe, reference).stream()
                .filter(need -> need.anyOf().stream().noneMatch(ownedToolIds::contains))
                .filter(need -> need.anyOf().stream()
                        .flatMap(toolId -> reference.toolRulesFor(recipe.id(), toolId).stream())
                        .noneMatch(rule -> ownedToolIds.contains(rule.substituteToolId())))
                .toList();
    }

    private static List<ActionRule> rulesFor(Recipe.Step step) {
        if (step.actionKey() == null) {
            return List.of();
        }
        return ACTION_RULES.stream().filter(rule -> rule.actionKeys().contains(step.actionKey())).toList();
    }

    private static Set<String> categoriesOf(List<String> toolIds, ReferenceData reference) {
        Set<String> categories = new LinkedHashSet<>();
        for (Category category : reference.toolCategories()) {
            if (category.variants().stream().anyMatch(variant -> toolIds.contains(variant.id()))) {
                categories.add(category.id());
            }
        }
        return categories;
    }
}
