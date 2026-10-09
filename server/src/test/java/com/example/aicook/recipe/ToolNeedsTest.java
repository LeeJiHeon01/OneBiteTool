package com.example.aicook.recipe;

import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.ToolNeeds.Need;
import com.example.aicook.reference.ReferenceData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tools a recipe needs when its steps do not name them (data/seed/action-tools.json). */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class ToolNeedsTest {

    @Autowired
    private ReferenceData reference;

    @Test
    void actionsWithoutNamedToolsStillNeedKnifeVesselAndStove() {
        Recipe recipe = recipe(step(1, "action.cut", null), step(2, "action.stir_fry", null),
                step(3, "action.add", null));

        List<Need> needs = ToolNeeds.of(recipe, reference);

        assertThat(needs).extracting(Need::id).containsExactly("knife", "cooking_vessel", ToolNeeds.STOVE);
        assertThat(needs.get(1).anyOf()).containsExactly("frying_pan", "pot", "earthen_pot");
        assertThat(needs.get(2).anyOf()).containsExactlyInAnyOrder("gas_stove", "induction");
    }

    @Test
    void impliedKnifeIsAStepToolSoSubstitutionAndSafetyNotesApply() {
        assertThat(ToolNeeds.stepTools(step(1, "action.cut", null), reference)).containsExactly("knife");
        // A step that already names a cutting tool keeps it.
        assertThat(ToolNeeds.stepTools(step(1, "action.cut", null, "scissors"), reference))
                .containsExactly("scissors");
        // A choice between several vessels is a need of the recipe, not a tool of the step.
        assertThat(ToolNeeds.stepTools(step(1, "action.stir_fry", null), reference)).isEmpty();
    }

    @Test
    void applianceStepNeedsNeitherPanNorStove() {
        Recipe recipe = recipe(step(1, "action.grill", null, "air_fryer"));

        assertThat(ToolNeeds.of(recipe, reference)).extracting(Need::id).containsExactly("air_fryer");
    }

    @Test
    void namedVesselIsNotAskedForTwice() {
        Recipe recipe = recipe(step(1, "action.boil", HeatLevel.MEDIUM, "pot"));

        assertThat(ToolNeeds.of(recipe, reference)).extracting(Need::id).containsExactly("pot", ToolNeeds.STOVE);
    }

    @Test
    void namedPanWithoutHeatLevelStillNeedsStove() {
        Recipe recipe = recipe(step(1, "action.stir_fry", null, "frying_pan"));

        assertThat(ToolNeeds.of(recipe, reference)).extracting(Need::id)
                .containsExactly("frying_pan", ToolNeeds.STOVE);
    }

    @Test
    void vesselNamedInOneStepCoversLaterStepsThatDoNotNameIt() {
        Recipe recipe = recipe(step(1, "action.add", null, "pot"), step(2, "action.boil", null));

        assertThat(ToolNeeds.of(recipe, reference)).extracting(Need::id).containsExactly("pot", ToolNeeds.STOVE);
    }

    @Test
    void stepWithoutActionKeyImpliesNothing() {
        assertThat(ToolNeeds.of(recipe(step(1, null, null)), reference)).isEmpty();
    }

    private static Recipe recipe(Recipe.Step... steps) {
        return new Recipe("RECIPE_TEST", "테스트", Recipe.Source.WEB_SEARCH, "https://example.com/a", "example.com",
                Recipe.VerificationStatus.IMPORTED, 1.0, null, List.of(), List.of(steps));
    }

    private static Recipe.Step step(int order, String actionKey, HeatLevel heatLevel, String... toolIds) {
        return new Recipe.Step("STEP_" + order, order, "행동", actionKey, null, null, heatLevel, null, null, null,
                List.of(), List.of(toolIds));
    }
}
