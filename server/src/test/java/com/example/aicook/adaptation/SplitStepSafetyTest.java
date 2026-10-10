package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.adaptation.AdaptationResponse.SafetyNote;
import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.common.Json;
import com.example.aicook.measure.MeasureConverter;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.RecipeDtos.DetailIngredient;
import com.example.aicook.recipe.RecipeService;
import com.example.aicook.reference.ReferenceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a step is shown as one step per action, each part gets the cautions and the allergy
 * ingredients that belong to it, decided by the server's rules and not by the AI. The fixture
 * also carries one time range, which must reach the adapted step.
 */
// The API key is cleared so the real AI is never called; a scripted client stands in for it.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class SplitStepSafetyTest {

    private static final String OIL_SPLASH = "뜨거운 기름이 튈 수 있어요.";
    private static final String DRY_PAN = "팬에 물기가 없는지 확인하세요.";
    private static final List<String> TOOLS = List.of("spoon_rice", "knife", "gas_stove", "frying_pan");

    @Autowired
    private RecipeService recipes;
    @Autowired
    private ReferenceData reference;
    @Autowired
    private MeasureConverter measureConverter;

    private String recipeId;
    private Map<String, String> ids;

    @BeforeEach
    void findFixture() {
        recipeId = recipes.search("김치볶음밥").get(0).id();
        ids = recipes.detail(recipeId).ingredients().stream()
                .collect(Collectors.toMap(DetailIngredient::name, DetailIngredient::id));
    }

    @Test
    void cautionsAndAllergyIngredientsFollowThePartTheyBelongTo() {
        // "썰어 둔 재료를 넣고 저어가며 볶으세요" is answered as frying the kimchi, then adding the green onion.
        ScriptedClient client = new ScriptedClient("저어가며 볶으세요", List.of(
                part("김치를 저어가며 볶아요", "action.stir_fry"), part("대파를 넣어요", "action.add")));

        List<AdaptedStep> steps = adapt(client).steps();

        AdaptedStep frying = stepSaying(steps, "김치를 저어가며 볶아요");
        AdaptedStep adding = stepSaying(steps, "대파를 넣어요");
        AdaptedStep heatingOil = steps.stream().filter(step -> "action.heat_oil".equals(step.actionKey()))
                .findFirst().orElseThrow();
        // The oil caution belongs to frying; adding an ingredient with no heat does not get it.
        assertThat(messages(frying)).contains(OIL_SPLASH);
        assertThat(messages(adding)).doesNotContain(OIL_SPLASH);
        assertThat(adding.heatLevel()).isNull();
        assertThat(adding.durationSeconds()).isNull();
        // Heating the oil keeps its own caution about a wet pan.
        assertThat(messages(heatingOil)).contains(DRY_PAN);
        // Kimchi (shrimp) is only in the part that fries it, so the allergy warning shows there only.
        assertThat(frying.ingredientIds()).containsExactly(ids.get("김치"));
        assertThat(adding.ingredientIds()).containsExactly(ids.get("대파"));
        // The time range of the fixture step stays with the frying.
        assertThat(frying.durationSeconds()).isEqualTo(180);
        assertThat(frying.durationMaxSeconds()).isEqualTo(240);
        assertThat(adding.durationMaxSeconds()).isNull();
    }

    @Test
    void fixtureTimeRangeReachesTheAdaptedStepWithoutTheAi() {
        ScriptedClient unavailable = new ScriptedClient(null, List.of());
        unavailable.available = false;

        List<AdaptedStep> steps = adapt(unavailable).steps();

        List<AdaptedStep> ranged = steps.stream().filter(step -> step.durationMaxSeconds() != null).toList();
        assertThat(ranged).singleElement().satisfies(step -> {
            assertThat(step.durationSeconds()).isEqualTo(180);
            assertThat(step.durationMaxSeconds()).isEqualTo(240);
        });
        // A step with a single time has no long end.
        assertThat(steps.stream().filter(step -> Integer.valueOf(120).equals(step.durationSeconds())))
                .allSatisfy(step -> assertThat(step.durationMaxSeconds()).isNull());
    }

    private AdaptationResponse adapt(ScriptedClient client) {
        AdaptationService service = new AdaptationService(recipes, reference, measureConverter,
                new StepRewriter(client, reference));
        return service.adapt(new AdaptationRequest(recipeId, 1, List.of(), List.of(),
                new Profile(SkillLevel.LOW, TOOLS, null, List.of())));
    }

    private static AdaptedStep stepSaying(List<AdaptedStep> steps, String action) {
        return steps.stream().filter(step -> step.action().equals(action)).findFirst().orElseThrow();
    }

    private static List<String> messages(AdaptedStep step) {
        return step.safetyNotes().stream().map(SafetyNote::message).toList();
    }

    private static Map<String, Object> part(String action, String actionKey) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("action", action);
        part.put("actionKeys", List.of(actionKey));
        part.put("details", null);
        return part;
    }

    /** Answers every step with its own sentence as one part, except the step that is split. */
    private static class ScriptedClient extends OpenAiClient {

        private final String splitStepContaining;
        private final List<Map<String, Object>> splitParts;
        private boolean available = true;

        ScriptedClient(String splitStepContaining, List<Map<String, Object>> splitParts) {
            super("", "test-model", 0, 1, null);
            this.splitStepContaining = splitStepContaining;
            this.splitParts = splitParts;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<String> complete(Purpose purpose, String promptName, Object input) {
            List<Map<String, Object>> answers = new ArrayList<>();
            for (Map<String, Object> step : (List<Map<String, Object>>) ((Map<String, Object>) input).get("steps")) {
                String action = (String) step.get("action");
                Object actionKey = step.get("actionKey") == null ? "action.other" : step.get("actionKey");
                Map<String, Object> answer = new LinkedHashMap<>();
                answer.put("stepId", step.get("stepId"));
                answer.put("parts", splitStepContaining != null && action.contains(splitStepContaining)
                        ? splitParts : List.of(part(action, (String) actionKey)));
                answers.add(answer);
            }
            return Optional.of(Json.write(Map.of("steps", answers)));
        }
    }
}
