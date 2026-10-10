package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.SafetyNote;
import com.example.aicook.adaptation.AdaptationResponse.SubItem;
import com.example.aicook.common.Korean;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Joins consecutive steps that do the same thing to different ingredients ("간장을 넣어요",
 * "참치액을 넣어요" becomes "간장, 참치액을 넣어요"). The result is still one action. Steps are
 * only joined when their sentences are identical apart from the ingredient, so nothing a
 * sentence says (how to cut, when to add) is lost; the stored recipe is never changed.
 */
final class StepMerger {

    private static final char SLOT = '\u0000';
    /** Action of finishing steps: putting the dish in a bowl, topping, sprinkling, serving on the side. */
    static final String PLATE_ACTION = "action.plate";
    private static final String PLATE_TEXT = "그릇에 담고 마무리해요";

    private StepMerger() {
    }

    private enum Particle {
        OBJECT("을", "를"),
        TOPIC("은", "는");

        private final String afterConsonant;
        private final String afterVowel;

        Particle(String afterConsonant, String afterVowel) {
            this.afterConsonant = afterConsonant;
            this.afterVowel = afterVowel;
        }

        String attach(String words) {
            return this == OBJECT ? Korean.object(words) : Korean.topic(words);
        }
    }

    /** The step's sentence with its one ingredient cut out, and the particle that followed it. */
    private record Shape(String template, Particle particle, String ingredientName) {
    }

    /**
     * @param ingredientNames ingredient id -> the name shown for it (the substitute's when substituted)
     */
    static List<AdaptedStep> merge(List<AdaptedStep> steps, Map<String, String> ingredientNames) {
        List<AdaptedStep> merged = new ArrayList<>();
        List<AdaptedStep> run = new ArrayList<>();
        for (AdaptedStep step : steps) {
            if (!run.isEmpty() && !canFollow(run, step, ingredientNames)) {
                merged.add(join(run, ingredientNames, merged.size() + 1));
                run.clear();
            }
            run.add(step);
        }
        if (!run.isEmpty()) {
            merged.add(join(run, ingredientNames, merged.size() + 1));
        }
        return merged;
    }

    /**
     * Joins finishing steps that follow one another into one step with a checklist. Each of them
     * is a small thing done to the finished dish, with no time or heat of its own.
     */
    static List<AdaptedStep> mergePlating(List<AdaptedStep> steps) {
        List<AdaptedStep> merged = new ArrayList<>();
        List<AdaptedStep> run = new ArrayList<>();
        for (AdaptedStep step : steps) {
            if (isPlating(step)) {
                run.add(step);
                continue;
            }
            flushPlating(run, merged);
            merged.add(step.withOrder(merged.size() + 1));
        }
        flushPlating(run, merged);
        return merged;
    }

    private static boolean isPlating(AdaptedStep step) {
        return PLATE_ACTION.equals(step.actionKey()) && step.durationSeconds() == null
                && step.timerStartCondition() == null && step.completionCheck() == null
                && step.heatLevel() == null && step.deviceSettingRaw() == null;
    }

    private static void flushPlating(List<AdaptedStep> run, List<AdaptedStep> merged) {
        if (run.size() == 1) {
            merged.add(run.get(0).withOrder(merged.size() + 1));
        } else if (run.size() > 1) {
            List<String> ingredientIds = run.stream().flatMap(step -> step.ingredientIds().stream()).distinct().toList();
            Set<SafetyNote> notes = new LinkedHashSet<>();
            run.forEach(step -> notes.addAll(step.safetyNotes()));
            List<SubItem> items = run.stream()
                    .map(step -> new SubItem(step.stepId(), step.action(), step.ingredientIds()))
                    .toList();
            merged.add(run.get(0).platedWith(merged.size() + 1, PLATE_TEXT, ingredientIds, List.copyOf(notes),
                    run.stream().map(AdaptedStep::stepId).toList(), items));
        }
        run.clear();
    }

    private static boolean canFollow(List<AdaptedStep> run, AdaptedStep next, Map<String, String> names) {
        AdaptedStep first = run.get(0);
        Shape firstShape = shapeOf(first, names);
        Shape nextShape = shapeOf(next, names);
        if (firstShape == null || nextShape == null) {
            return false;
        }
        boolean newIngredient = run.stream().noneMatch(step -> step.ingredientIds().equals(next.ingredientIds()));
        return newIngredient
                && firstShape.template().equals(nextShape.template())
                && firstShape.particle() == nextShape.particle()
                && first.actionKey() != null && first.actionKey().equals(next.actionKey())
                // A step with its own time or check stays on its own; so does one with other heat,
                // other tools or other safety notes.
                && first.durationSeconds() == null && next.durationSeconds() == null
                && first.timerStartCondition() == null && next.timerStartCondition() == null
                && first.completionCheck() == null && next.completionCheck() == null
                && first.heatLevel() == next.heatLevel()
                && Objects.equals(first.heatRaw(), next.heatRaw())
                && Objects.equals(first.deviceSettingRaw(), next.deviceSettingRaw())
                && first.toolIds().equals(next.toolIds())
                && first.safetyNotes().equals(next.safetyNotes());
    }

    /** Null unless the step has exactly one ingredient that the sentence names once, followed by 을/를 or 은/는. */
    private static Shape shapeOf(AdaptedStep step, Map<String, String> names) {
        if (step.ingredientIds().size() != 1) {
            return null;
        }
        String name = names.get(step.ingredientIds().get(0));
        String action = step.action();
        if (name == null || name.isBlank()) {
            return null;
        }
        int at = action.indexOf(name);
        if (at < 0 || at != action.lastIndexOf(name)) {
            return null;
        }
        String rest = action.substring(at + name.length());
        for (Particle particle : Particle.values()) {
            for (String form : List.of(particle.afterConsonant, particle.afterVowel)) {
                if (rest.startsWith(form)) {
                    String template = action.substring(0, at) + SLOT + rest.substring(form.length());
                    return new Shape(template, particle, name);
                }
            }
        }
        return null;
    }

    private static AdaptedStep join(List<AdaptedStep> run, Map<String, String> names, int order) {
        AdaptedStep first = run.get(0);
        if (run.size() == 1) {
            return first.withOrder(order);
        }
        Shape shape = shapeOf(first, names);
        String listed = String.join(", ", run.stream().map(step -> shapeOf(step, names).ingredientName()).toList());
        String action = shape.template().replace(String.valueOf(SLOT), shape.particle().attach(listed));

        List<String> ingredientIds = run.stream().flatMap(step -> step.ingredientIds().stream()).distinct().toList();
        Set<SafetyNote> notes = new LinkedHashSet<>();
        run.forEach(step -> notes.addAll(step.safetyNotes()));
        return first.mergedWith(order, action, ingredientIds, List.copyOf(notes),
                run.stream().map(AdaptedStep::stepId).toList());
    }
}
