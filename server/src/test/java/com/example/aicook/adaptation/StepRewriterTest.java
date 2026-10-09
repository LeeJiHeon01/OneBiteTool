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

    private static String rewrite(String stepId, String action, String details) {
        String detailsJson = details == null ? "null" : "\"" + details + "\"";
        return "{\"stepId\":\"" + stepId + "\",\"action\":\"" + action + "\",\"details\":" + detailsJson + "}";
    }

    private static AdaptedStep step(String id, String action, List<String> toolIds, HeatLevel heatLevel,
                                    Integer durationSeconds) {
        return new AdaptedStep(id, 1, action, "action.stir_fry", null, List.of("ING_KIMCHI"), toolIds, List.of(), durationSeconds, null,
                heatLevel, null, null, null, "김치 부피가 줄면 다음으로 가세요.",
                List.of(new SafetyNote("기름이 튈 수 있어요.", null)));
    }

    private static Variant tool(String id, String name) {
        return new Variant(id, name, null, "tool." + id, null, null, false, false, true, null, null, null, List.of());
    }

    private static Ingredient ingredient(String code, String name) {
        return new Ingredient(code, name, "ETC", "SOLID", Sensitivity.HIGH, false, null, false);
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
