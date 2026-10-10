package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedIngredient;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.Display;
import com.example.aicook.adaptation.AdaptationResponse.Original;
import com.example.aicook.adaptation.AdaptationResponse.SafetyNote;
import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Rules.Ingredient;
import com.example.aicook.reference.Sensitivity;
import com.example.aicook.reference.Tools.Category;
import com.example.aicook.reference.Tools.Variant;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** AI rewording of steps: what is accepted, what falls back to the server's sentence (no real API calls). */
class StepRewriterTest {

    private static final ReferenceData REFERENCE = new ReferenceData(
            List.of(new Category("all", "도구", List.of(tool("frying_pan", "프라이팬"), tool("knife", "칼"),
                    tool("scissors", "가위")))),
            null,
            List.of(ingredient("kimchi", "김치"), ingredient("soy_sauce", "간장"), ingredient("sugar", "설탕")),
            Set.of(), Map.of(), List.of(), List.of(), List.of(), List.of());

    private static final List<AdaptedIngredient> INGREDIENTS = List.of(
            new AdaptedIngredient("ING_KIMCHI", "김치", new Original(200.0, "g", "200g"),
                    new Display("rice_bowl", null, 1.0, "밥공기로 가득 1번 (200g)", false), null, Sensitivity.HIGH, false));

    private static final AdaptedStep CUT = step("S1", "김치를 먹기 좋게 썬다", List.of("knife"), null, null);
    private static final AdaptedStep FRY = step("S2", "김치를 볶는다", List.of("frying_pan"), HeatLevel.MEDIUM, 180);

    @Test
    void acceptedTextReplacesActionAndAddsDetails() {
        FakeClient client = new FakeClient(answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", "칼을 앞뒤로 움직이며 썰면 쉬워요"),
                rewrite("S2", "김치를 저어가며 볶아요", null)));

        List<AdaptedStep> steps = rewrite(client, SkillLevel.LOW);

        assertThat(steps.get(0).action()).isEqualTo("김치를 먹기 좋은 크기로 썰어요");
        assertThat(steps.get(0).details()).isEqualTo("칼을 앞뒤로 움직이며 썰면 쉬워요");
        assertThat(steps.get(1).action()).isEqualTo("김치를 저어가며 볶아요");
        assertThat(client.calls).isEqualTo(1);
    }

    @Test
    void timeHeatCheckAndSafetyNotesAreNeverChanged() {
        FakeClient client = new FakeClient(answer(rewrite("S1", "김치를 썰어요", null),
                rewrite("S2", "김치를 볶아요", null)));

        AdaptedStep fried = rewrite(client, SkillLevel.LOW).get(1);

        assertThat(fried.durationSeconds()).isEqualTo(180);
        assertThat(fried.heatLevel()).isEqualTo(HeatLevel.MEDIUM);
        assertThat(fried.completionCheck()).isEqualTo(FRY.completionCheck());
        assertThat(fried.safetyNotes()).isEqualTo(FRY.safetyNotes());
        assertThat(fried.ingredientIds()).isEqualTo(FRY.ingredientIds());
    }

    @Test
    void textWithNumberHeatSafetyWordOrOtherIngredientKeepsServerSentence() {
        for (String bad : List.of("김치를 3분 볶아요", "김치를 중불에서 볶아요", "김치를 조심해서 볶아요",
                "김치와 설탕을 볶아요", "가위로 김치를 볶아요")) {
            String invalid = answer(rewrite("S1", "김치를 썰어요", null), rewrite("S2", bad, null));
            FakeClient client = new FakeClient(invalid, invalid);

            List<AdaptedStep> steps = rewrite(client, SkillLevel.LOW);

            assertThat(steps.get(0).action()).as(bad).isEqualTo("김치를 썰어요");
            assertThat(steps.get(1).action()).as(bad).isEqualTo(FRY.action());
            // One retry, then the server's own sentence.
            assertThat(client.calls).as(bad).isEqualTo(2);
        }
    }

    @Test
    void retryFillsInTheStepThatFailedTheFirstTime() {
        FakeClient client = new FakeClient(
                answer(rewrite("S1", "김치를 썰어요", null), rewrite("S2", "김치를 3분 볶아요", null)),
                answer(rewrite("S1", "김치를 잘라요", null), rewrite("S2", "김치를 저어가며 볶아요", null)));

        List<AdaptedStep> steps = rewrite(client, SkillLevel.MID);

        assertThat(steps.get(1).action()).isEqualTo("김치를 저어가며 볶아요");
        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    void answerWithMissingReorderedOrUnreadableStepsIsDropped() {
        for (String broken : List.of(answer(rewrite("S1", "김치를 썰어요", null)),
                answer(rewrite("S2", "김치를 볶아요", null), rewrite("S1", "김치를 썰어요", null)), "not json")) {
            List<AdaptedStep> steps = rewrite(new FakeClient(broken, broken), SkillLevel.LOW);

            assertThat(steps).as(broken).containsExactly(CUT, FRY);
        }
    }

    @Test
    void experiencedCooksNeverGetDetails() {
        FakeClient client = new FakeClient(answer(rewrite("S1", "김치를 썰어요", "칼을 앞뒤로 움직여요"),
                rewrite("S2", "김치를 볶아요", "  ")));

        List<AdaptedStep> steps = rewrite(client, SkillLevel.HIGH);

        assertThat(steps).extracting(AdaptedStep::details).containsOnlyNulls();
        assertThat(steps.get(0).action()).isEqualTo("김치를 썰어요");
    }

    @Test
    void stepWithTwoActionsBecomesOneStepPerAction() {
        FakeClient client = new FakeClient(answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 볶아요", null, "action.stir_fry"), part("김치를 접시에 덜어요", null, "action.add"))));

        List<AdaptedStep> steps = rewrite(client, SkillLevel.LOW);

        assertThat(steps).extracting(AdaptedStep::action)
                .containsExactly("김치를 먹기 좋은 크기로 썰어요", "김치를 볶아요", "김치를 접시에 덜어요");
        assertThat(steps).extracting(AdaptedStep::order).containsExactly(1, 2, 3);
        assertThat(steps).extracting(AdaptedStep::stepId).containsExactly("S1", "S2-1", "S2-2");
        AdaptedStep frying = steps.get(1);
        AdaptedStep adding = steps.get(2);
        // No time or heat is made up: they stay with the action they were given for.
        assertThat(adding.durationSeconds()).isNull();
        assertThat(adding.heatLevel()).isNull();
        assertThat(adding.completionCheck()).isNull();
        assertThat(frying.durationSeconds()).isEqualTo(FRY.durationSeconds());
        assertThat(frying.heatLevel()).isEqualTo(FRY.heatLevel());
        assertThat(frying.completionCheck()).isEqualTo(FRY.completionCheck());
        assertThat(frying.actionKey()).isEqualTo("action.stir_fry");
        assertThat(adding.actionKey()).isEqualTo("action.add");
        // Both sentences name the ingredient, so both keep it; tools stay on every part.
        for (AdaptedStep part : List.of(adding, frying)) {
            assertThat(part.ingredientIds()).isEqualTo(FRY.ingredientIds());
            assertThat(part.toolIds()).isEqualTo(FRY.toolIds());
            assertThat(part.mergedFromStepIds()).containsExactly("S2");
        }
        // Without the rules to check a part against, every caution of the step stays.
        assertThat(frying.safetyNotes()).isEqualTo(FRY.safetyNotes());
        assertThat(adding.safetyNotes()).isEqualTo(FRY.safetyNotes());
    }

    @Test
    void puttingInAndCookingAtOnceIsOneStep() {
        FakeClient client = new FakeClient(answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 넣고 볶아요", null, "action.add", "action.stir_fry"))));

        List<AdaptedStep> steps = rewrite(client, SkillLevel.LOW);

        assertThat(steps).hasSize(2);
        assertThat(steps.get(1).action()).isEqualTo("김치를 넣고 볶아요");
        // Counted as the cooking alone: time, heat and the check stay where they were.
        assertThat(steps.get(1).actionKey()).isEqualTo(FRY.actionKey());
        assertThat(steps.get(1).durationSeconds()).isEqualTo(FRY.durationSeconds());
        assertThat(steps.get(1).heatLevel()).isEqualTo(FRY.heatLevel());
        assertThat(client.calls).isEqualTo(1);
    }

    @Test
    void addingSplitOffRightBeforeCookingIsRejected() {
        // "넣어요" then "볶아요" for one ingredient only makes more steps for the same thing.
        FakeClient client = new FakeClient(answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 넣어요", null, "action.add"), part("김치를 볶아요", null, "action.stir_fry"))));

        List<AdaptedStep> steps = rewrite(client, SkillLevel.LOW);

        assertThat(steps).hasSize(2);
        assertThat(steps.get(1)).isEqualTo(FRY);
    }

    @Test
    void manyIngredientsGoingInAtOnceAreAStepOfTheirOwn() {
        List<String> ids = List.of("ING_KIMCHI", "ING_A", "ING_B", "ING_C", "ING_D");
        List<AdaptedIngredient> five = ids.stream()
                .map(id -> new AdaptedIngredient(id, id.equals("ING_KIMCHI") ? "김치" : "재료" + id.substring(4),
                        new Original(null, null, "약간"), new Display(null, null, null, "약간", false), null,
                        Sensitivity.NORMAL, false))
                .toList();
        AdaptedStep stew = new AdaptedStep("S9", 1, "재료를 모두 넣고 끓인다", "action.boil", null, ids,
                List.of("frying_pan"), List.of(), 300, null, HeatLevel.MEDIUM, null, null, null, null, List.of(),
                List.of());
        String together = answer(split("S9", part("재료를 모두 넣고 끓여요", null, "action.add", "action.boil")));
        String apart = answer(split("S9", part("재료를 모두 넣어요", null, "action.add"),
                part("끓여요", null, "action.boil")));

        // Five ingredients in one go: "put in and cook" as one sentence is not accepted.
        List<AdaptedStep> kept = new StepRewriter(new FakeClient(together, together), REFERENCE)
                .rewrite(SkillLevel.LOW, List.of(stew), five, List.of());
        assertThat(kept).containsExactly(stew);

        List<AdaptedStep> split = new StepRewriter(new FakeClient(apart), REFERENCE)
                .rewrite(SkillLevel.LOW, List.of(stew), five, List.of());
        assertThat(split).extracting(AdaptedStep::action).containsExactly("재료를 모두 넣어요", "끓여요");
        assertThat(split.get(0).durationSeconds()).isNull();
        assertThat(split.get(1).durationSeconds()).isEqualTo(300);
    }

    @Test
    void ingredientsAndCautionsFollowThePartThatHandlesThem() {
        AdaptedIngredient kimchi = INGREDIENTS.get(0);
        AdaptedIngredient sugar = new AdaptedIngredient("ING_SUGAR", "설탕", new Original(null, null, "약간"),
                new Display(null, null, null, "약간", false), null, Sensitivity.HIGH, false);
        AdaptedIngredient oil = new AdaptedIngredient("ING_OIL", "기름", new Original(null, null, "약간"),
                new Display(null, null, null, "약간", false), null, Sensitivity.NORMAL, false);
        SafetyNote splash = new SafetyNote("뜨거운 기름이 튈 수 있어요.", null);
        AdaptedStep step = new AdaptedStep("S7", 1, "김치를 볶다가 설탕을 넣는다", "action.stir_fry", null,
                List.of("ING_KIMCHI", "ING_SUGAR", "ING_OIL"), List.of("frying_pan"), List.of(), null, null,
                HeatLevel.MEDIUM, null, null, null, null, List.of(splash), List.of());
        FakeClient client = new FakeClient(answer(split("S7",
                part("김치를 볶아요", null, "action.stir_fry"), part("설탕을 넣어요", null, "action.add"))));
        // The server's own rule: the oil caution belongs to frying.
        StepRewriter.PartNotes byAction = (original, partActionKey, partIngredientIds, anchor, partActionKeys) ->
                "action.stir_fry".equals(partActionKey) ? List.of(splash) : List.of();

        List<AdaptedStep> steps = new StepRewriter(client, REFERENCE)
                .rewrite(SkillLevel.LOW, List.of(step), List.of(kimchi, sugar, oil), List.of(), byAction);

        AdaptedStep frying = steps.get(0);
        AdaptedStep adding = steps.get(1);
        assertThat(frying.safetyNotes()).containsExactly(splash);
        // No heat, no oil caution on "설탕을 넣어요".
        assertThat(adding.safetyNotes()).isEmpty();
        assertThat(adding.heatLevel()).isNull();
        // An ingredient goes to the part that names it; one that no part names stays on every part.
        assertThat(frying.ingredientIds()).containsExactly("ING_KIMCHI", "ING_OIL");
        assertThat(adding.ingredientIds()).containsExactly("ING_SUGAR", "ING_OIL");
    }

    @Test
    void sentenceWithTwoMainActionsIsRejectedAndAskedAgain() {
        String twoInOne = answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 볶고 섞어요", null, "action.stir_fry", "action.mix")));
        String fixed = answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 볶아요", null, "action.stir_fry"), part("김치를 섞어요", null, "action.mix")));

        FakeClient retried = new FakeClient(twoInOne, fixed);
        assertThat(rewrite(retried, SkillLevel.LOW)).extracting(AdaptedStep::action)
                .containsExactly("김치를 먹기 좋은 크기로 썰어요", "김치를 볶아요", "김치를 섞어요");
        assertThat(retried.calls).isEqualTo(2);

        // Still two actions after the retry: the server's own sentence stays, unsplit.
        FakeClient stubborn = new FakeClient(twoInOne, twoInOne);
        List<AdaptedStep> kept = rewrite(stubborn, SkillLevel.LOW);
        assertThat(kept).hasSize(2);
        assertThat(kept.get(1)).isEqualTo(FRY);
        assertThat(stubborn.calls).isEqualTo(2);
    }

    @Test
    void splitThatLeavesTheTimerWithoutAnActionIsRejected() {
        // Neither part is the frying the timer and heat were given for.
        FakeClient noAnchor = new FakeClient(answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 넣어요", null, "action.add"), part("김치를 섞어요", null, "action.mix"))));
        // Both parts claim it.
        FakeClient twoAnchors = new FakeClient(answer(
                rewrite("S1", "김치를 먹기 좋은 크기로 썰어요", null),
                split("S2", part("김치를 볶아요", null, "action.stir_fry"),
                        part("김치를 더 볶아요", null, "action.stir_fry"))));

        assertThat(rewrite(noAnchor, SkillLevel.LOW).get(1)).isEqualTo(FRY);
        assertThat(rewrite(twoAnchors, SkillLevel.LOW).get(1)).isEqualTo(FRY);
    }

    @Test
    void failedOrUnavailableAiKeepsServerSentences() {
        FakeClient failing = new FakeClient();
        assertThat(rewrite(failing, SkillLevel.LOW)).containsExactly(CUT, FRY);
        assertThat(failing.calls).isEqualTo(1);

        FakeClient unavailable = new FakeClient();
        unavailable.available = false;
        assertThat(rewrite(unavailable, SkillLevel.LOW)).containsExactly(CUT, FRY);
        assertThat(unavailable.calls).isZero();
    }

    private static List<AdaptedStep> rewrite(FakeClient client, SkillLevel skillLevel) {
        return new StepRewriter(client, REFERENCE).rewrite(skillLevel, List.of(CUT, FRY), INGREDIENTS, List.of());
    }

    private static String answer(String... rewrites) {
        return "{\"steps\":[" + String.join(",", rewrites) + "]}";
    }

    /** A step answered as one part with the step's own action. */
    private static String rewrite(String stepId, String action, String details) {
        return "{\"stepId\":\"" + stepId + "\",\"parts\":[" + part(action, details, "action.stir_fry") + "]}";
    }

    private static String split(String stepId, String... parts) {
        return "{\"stepId\":\"" + stepId + "\",\"parts\":[" + String.join(",", parts) + "]}";
    }

    private static String part(String action, String details, String... actionKeys) {
        String detailsJson = details == null ? "null" : "\"" + details + "\"";
        String keys = List.of(actionKeys).stream().map(key -> "\"" + key + "\"").collect(Collectors.joining(","));
        return "{\"action\":\"" + action + "\",\"actionKeys\":[" + keys + "],\"details\":" + detailsJson + "}";
    }

    private static AdaptedStep step(String id, String action, List<String> toolIds, HeatLevel heatLevel,
                                    Integer durationSeconds) {
        return new AdaptedStep(id, 1, action, "action.stir_fry", null, List.of("ING_KIMCHI"), toolIds, List.of(), durationSeconds, null,
                heatLevel, null, null, null, "김치 부피가 줄면 다음으로 가세요.",
                List.of(new SafetyNote("기름이 튈 수 있어요.", null)), List.of());
    }

    private static Variant tool(String id, String name) {
        return new Variant(id, name, null, "tool." + id, null, null, false, false, true, null, null, null, List.of());
    }

    private static Ingredient ingredient(String code, String name) {
        return new Ingredient(code, name, "ETC", "SOLID", Sensitivity.HIGH, false, null, false, List.of());
    }

    /** Returns the prepared answers in order; an empty Optional once they run out. */
    private static class FakeClient extends OpenAiClient {

        private final Deque<String> answers;
        private boolean available = true;
        private int calls;

        FakeClient(String... answers) {
            super("", "test-model", 0, 1, null);
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public Optional<String> complete(Purpose purpose, String promptName, Object input) {
            calls++;
            return Optional.ofNullable(answers.poll());
        }
    }
}
