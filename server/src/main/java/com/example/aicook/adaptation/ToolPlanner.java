package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.Change;
import com.example.aicook.adaptation.AdaptationResponse.ChangeType;
import com.example.aicook.adaptation.AdaptationResponse.ItemKind;
import com.example.aicook.adaptation.AdaptationResponse.RequiredItem;
import com.example.aicook.common.Korean;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.recipe.ToolNeeds;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.ToolRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the tools a recipe needs against the tools the user owns. A missing tool is either
 * replaced through a verified rule or reported as required; it is never silently ignored.
 */
class ToolPlanner {

    /** substitutions maps a recipe tool id to the rule that replaces it. */
    record Plan(Map<String, ToolRule> substitutions, List<Change> changes, List<RequiredItem> requiredItems) {

        String toolFor(String recipeToolId) {
            ToolRule rule = substitutions.get(recipeToolId);
            return rule == null ? recipeToolId : rule.substituteToolId();
        }
    }

    private final ReferenceData reference;

    ToolPlanner(ReferenceData reference) {
        this.reference = reference;
    }

    Plan plan(Recipe recipe, Set<String> ownedToolIds) {
        Map<String, ToolRule> substitutions = new HashMap<>();
        List<Change> changes = new ArrayList<>();
        List<RequiredItem> required = new ArrayList<>();

        for (ToolNeeds.Need need : ToolNeeds.of(recipe, reference)) {
            if (need.anyOf().stream().anyMatch(ownedToolIds::contains)) {
                continue;
            }
            Optional<ToolRule> rule = findRule(recipe, need, ownedToolIds);
            if (rule.isPresent()) {
                substitutions.put(rule.get().requiredToolId(), rule.get());
                changes.add(toChange(rule.get()));
            } else {
                required.add(new RequiredItem(ItemKind.TOOL, need.id(), need.label(), need.reason()));
            }
        }
        return new Plan(substitutions, changes, required);
    }

    private Optional<ToolRule> findRule(Recipe recipe, ToolNeeds.Need need, Set<String> ownedToolIds) {
        return need.anyOf().stream()
                .flatMap(toolId -> reference.toolRulesFor(recipe.id(), toolId).stream())
                .filter(rule -> ownedToolIds.contains(rule.substituteToolId()))
                .findFirst();
    }

    private Change toChange(ToolRule rule) {
        String description = nameOf(rule.requiredToolId()) + " 대신 " + Korean.object(nameOf(rule.substituteToolId()))
                + " 써요.";
        return new Change(ChangeType.TOOL_SUBSTITUTE, rule.requiredToolId(), description, rule.conditionNote());
    }

    private String nameOf(String toolId) {
        return reference.tool(toolId).orElseThrow().name();
    }
}
