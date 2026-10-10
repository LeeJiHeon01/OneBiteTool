package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.SafetyNote;
import com.example.aicook.recipe.Recipe.HeatLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Joining consecutive steps that do the same thing to different ingredients (spec 8.3.1). */
class StepMergerTest {

    private static final Map<String, String> NAMES = Map.of("I1", "간장", "I2", "참치액", "I3", "메이플시럽",
            "I4", "두부", "I5", "양파", "I6", "대파");
    private static final SafetyNote KNIFE = new SafetyNote("칼을 쓸 때는 손가락을 조심하세요.", null);

    @Test
    void sameSentenceOnDifferentIngredientsBecomesOneStep() {
        List<AdaptedStep> merged = StepMerger.merge(List.of(
                step("S1", "간장을 넣어요", "action.add", "I1"),
                step("S2", "참치액을 넣어요", "action.add", "I2"),
                step("S3", "메이플시럽을 넣어요", "action.add", "I3")), NAMES);

        assertThat(merged).singleElement().satisfies(step -> {
            assertThat(step.stepId()).isEqualTo("S1");
            assertThat(step.order()).isEqualTo(1);
            assertThat(step.action()).isEqualTo("간장, 참치액, 메이플시럽을 넣어요");
            assertThat(step.ingredientIds()).containsExactly("I1", "I2", "I3");
            assertThat(step.mergedFromStepIds()).containsExactly("S1", "S2", "S3");
        });
    }

    @Test
    void particleFollowsTheLastIngredientOfTheList() {
        List<AdaptedStep> merged = StepMerger.merge(List.of(
                step("S1", "양파를 썰어요", "action.cut", "I5"),
                step("S2", "대파를 썰어요", "action.cut", "I6"),
                step("S3", "간장을 썰어요", "action.cut", "I1")), NAMES);

        assertThat(merged).singleElement()
                .satisfies(step -> assertThat(step.action()).isEqualTo("양파, 대파, 간장을 썰어요"));
    }

    @Test
    void sentencesThatSayDifferentThingsAreKeptApart() {
        // How to cut differs, so joining would lose it.
        List<AdaptedStep> steps = List.of(
                step("S1", "두부를 크게 썰어요", "action.cut", "I4"),
                step("S2", "양파를 얇게 썰어요", "action.cut", "I5"));

        assertThat(StepMerger.merge(steps, NAMES)).extracting(AdaptedStep::action)
                .containsExactly("두부를 크게 썰어요", "양파를 얇게 썰어요");
    }

    @Test
    void stepsWithTimeOtherHeatOtherSafetyNotesOrACheckAreKeptApart() {
        AdaptedStep plain = step("S1", "간장을 넣어요", "action.add", "I1");
        List<AdaptedStep> different = List.of(
                new AdaptedStep("S2", 2, "참치액을 넣어요", "action.add", null, List.of("I2"), List.of(), List.of(),
                        60, null, null, null, null, null, null, List.of(), List.of()),
                new AdaptedStep("S2", 2, "참치액을 넣어요", "action.add", null, List.of("I2"), List.of(), List.of(),
                        null, null, HeatLevel.LOW, "약불", null, null, null, List.of(), List.of()),
                new AdaptedStep("S2", 2, "참치액을 넣어요", "action.add", null, List.of("I2"), List.of(), List.of(),
                        null, null, null, null, null, null, null, List.of(KNIFE), List.of()),
                new AdaptedStep("S2", 2, "참치액을 넣어요", "action.add", null, List.of("I2"), List.of(), List.of(),
                        null, null, null, null, null, null, "색이 진해지면", List.of(), List.of()),
                step("S2", "참치액을 넣어요", "action.mix", "I2"));

        for (AdaptedStep other : different) {
            assertThat(StepMerger.merge(List.of(plain, other), NAMES)).hasSize(2);
        }
    }

    @Test
    void safetyNotesOfAJoinedStepAreAllKept() {
        List<AdaptedStep> merged = StepMerger.merge(List.of(
                withNotes("S1", "양파를 썰어요", "I5", KNIFE),
                withNotes("S2", "대파를 썰어요", "I6", KNIFE)), NAMES);

        assertThat(merged).singleElement().satisfies(step -> assertThat(step.safetyNotes()).containsExactly(KNIFE));
    }

    @Test
    void stepsThatAreNotNextToEachOtherAreNotJoinedAndOrderIsRenumbered() {
        List<AdaptedStep> merged = StepMerger.merge(List.of(
                step("S1", "간장을 넣어요", "action.add", "I1"),
                step("S2", "참치액을 넣어요", "action.add", "I2"),
                step("S3", "두부를 구워요", "action.grill", "I4"),
                step("S4", "메이플시럽을 넣어요", "action.add", "I3")), NAMES);

        assertThat(merged).extracting(AdaptedStep::action)
                .containsExactly("간장, 참치액을 넣어요", "두부를 구워요", "메이플시럽을 넣어요");
        assertThat(merged).extracting(AdaptedStep::order).containsExactly(1, 2, 3);
        assertThat(merged.get(1).mergedFromStepIds()).isEmpty();
    }

    @Test
    void stepWithSeveralOrNoIngredientsOrNoActionKeyIsLeftAlone() {
        List<AdaptedStep> steps = List.of(
                new AdaptedStep("S1", 1, "간장과 참치액을 넣어요", "action.add", null, List.of("I1", "I2"), List.of(),
                        List.of(), null, null, null, null, null, null, null, List.of(), List.of()),
                step("S2", "메이플시럽을 넣어요", "action.add", "I3"),
                new AdaptedStep("S3", 3, "다시 말아요", null, null, List.of(), List.of(), List.of(), null, null,
                        null, null, null, null, null, List.of(), List.of()),
                step("S4", "간장을 말아요", null, "I1"),
                step("S5", "참치액을 말아요", null, "I2"));

        assertThat(StepMerger.merge(steps, NAMES)).hasSize(5);
    }

    @Test
    void finishingStepsInARowBecomeOneStepWithAChecklist() {
        List<AdaptedStep> merged = StepMerger.mergePlating(List.of(
                step("S1", "두부를 구워요", "action.grill", "I4"),
                step("S2", "밥을 그릇에 담아요", "action.plate", "I5"),
                step("S3", "두부를 밥 위에 올려요", "action.plate", "I4"),
                step("S4", "양파를 볶아요", "action.stir_fry", "I5")));

        assertThat(merged).extracting(AdaptedStep::order).containsExactly(1, 2, 3);
        AdaptedStep plate = merged.get(1);
        assertThat(plate.order()).isEqualTo(2);
        assertThat(plate.stepId()).isEqualTo("S2");
        assertThat(plate.actionKey()).isEqualTo("action.plate");
        assertThat(plate.action()).isEqualTo("그릇에 담고 마무리해요");
        assertThat(plate.mergedFromStepIds()).containsExactly("S2", "S3");
        assertThat(plate.ingredientIds()).containsExactly("I5", "I4");
        assertThat(plate.subItems()).extracting(item -> item.stepId() + ":" + item.text())
                .containsExactly("S2:밥을 그릇에 담아요", "S3:두부를 밥 위에 올려요");
        assertThat(plate.subItems().get(1).ingredientIds()).containsExactly("I4");
    }

    @Test
    void finishingStepWithATimeOrOnItsOwnIsLeftAsItIs() {
        AdaptedStep timed = new AdaptedStep("S2", 2, "한 김 식혀요", "action.plate", null, List.of("I4"), List.of(),
                List.of(), 300, null, null, null, null, null, null, List.of(), List.of());
        List<AdaptedStep> merged = StepMerger.mergePlating(List.of(
                step("S1", "밥을 그릇에 담아요", "action.plate", "I5"), timed,
                step("S3", "깨를 뿌려요", "action.plate", "I6")));

        assertThat(merged).hasSize(3);
        assertThat(merged).allSatisfy(step -> assertThat(step.subItems()).isEmpty());
    }

    @Test
    void safetyNotesOfEveryFinishingStepAreKept() {
        List<AdaptedStep> merged = StepMerger.mergePlating(List.of(
                new AdaptedStep("S1", 1, "밥을 그릇에 담아요", "action.plate", null, List.of("I5"), List.of(),
                        List.of(), null, null, null, null, null, null, null, List.of(KNIFE), List.of()),
                step("S2", "깨를 뿌려요", "action.plate", "I6")));

        assertThat(merged).singleElement().satisfies(step -> assertThat(step.safetyNotes()).containsExactly(KNIFE));
    }

    private static AdaptedStep step(String id, String action, String actionKey, String ingredientId) {
        return new AdaptedStep(id, 1, action, actionKey, null, List.of(ingredientId), List.of(), List.of(), null,
                null, null, null, null, null, null, List.of(), List.of());
    }

    private static AdaptedStep withNotes(String id, String action, String ingredientId, SafetyNote... notes) {
        return new AdaptedStep(id, 1, action, "action.cut", null, List.of(ingredientId), List.of("knife"),
                List.of(), null, null, null, null, null, null, null, List.of(notes), List.of());
    }
}
