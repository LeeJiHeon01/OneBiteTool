package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.adaptation.AdaptationResponse.AdaptedStep;
import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.common.ApiException;
import com.example.aicook.common.Json;
import com.example.aicook.measure.MeasureConverter;
import com.example.aicook.recipe.Recipe.HeatLevel;
import com.example.aicook.recipe.Recipe.SkillLevel;
import com.example.aicook.recipe.RecipeService;
import com.example.aicook.reference.ReferenceData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stored adaptations: the same recipe under the same conditions is answered again without the
 * AI, nothing about the user's allergies is stored, and a stored answer is not used once the
 * reference data, the prompt or the model changed.
 */
// The API key is cleared so the real AI is never called; a counting client stands in for it.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
class AdaptationCacheTest {

    private static final List<String> TOOLS = List.of("spoon_rice", "knife", "gas_stove", "frying_pan");

    @Autowired
    private RecipeService recipes;
    @Autowired
    private ReferenceData reference;
    @Autowired
    private MeasureConverter measureConverter;
    @Autowired
    private AdaptationCache cache;
    @Autowired
    private CacheVersions versions;
    @Autowired
    private JdbcClient jdbc;

    private String recipeId;
    private CountingClient client;
    private AdaptationService service;

    @BeforeEach
    void setUp() {
        recipeId = recipes.search("김치볶음밥").get(0).id();
        cleanUp();
        client = new CountingClient();
        service = serviceWith(versions);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM adapted_recipe WHERE recipe_id = :id").param("id", recipeId).update();
    }

    @Test
    void sameConditionsAreAnsweredAgainWithoutTheAi() {
        AdaptationResponse first = adapt(SkillLevel.LOW, List.of());
        int callsAfterFirst = client.calls;
        long startedAt = System.nanoTime();
        AdaptationResponse second = adapt(SkillLevel.LOW, List.of());
        long millis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(callsAfterFirst).isEqualTo(1);
        // No AI call for the second answer, and it is the stored one.
        assertThat(client.calls).isEqualTo(callsAfterFirst);
        assertThat(first.id()).matches("ADAPT_[0-9]{14}");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.steps()).isEqualTo(first.steps());
        assertThat(second.ingredients()).isEqualTo(first.ingredients());
        assertThat(second.safetySummary()).isEqualTo(first.safetySummary());
        // Read from the database instead of waiting for the AI.
        assertThat(millis).isLessThan(1000);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void otherConditionsAreNotServedFromTheSameEntry() {
        adapt(SkillLevel.LOW, List.of());
        adapt(SkillLevel.HIGH, List.of());
        service.adapt(new AdaptationRequest(recipeId, 1, List.of(), List.of(),
                new Profile(SkillLevel.LOW, List.of("spoon_rice", "knife", "induction", "frying_pan"), null, List.of())));

        assertThat(client.calls).isEqualTo(3);
        assertThat(rows()).isEqualTo(3);
    }

    @Test
    void allergiesAreNeitherInTheKeyNorInTheStoredAnswer() {
        // Kimchi carries shrimp; the first request comes from someone who avoids it.
        AdaptationResponse withAllergy = adapt(SkillLevel.LOW, List.of("shrimp"));
        AdaptationResponse withoutAllergy = adapt(SkillLevel.LOW, List.of());

        // One entry serves both: allergies are not part of the key.
        assertThat(client.calls).isEqualTo(1);
        assertThat(withoutAllergy.id()).isEqualTo(withAllergy.id());
        // The warning is worked out for the request that states the allergy, every time.
        assertThat(withAllergy.allergyWarnings()).isNotEmpty();
        assertThat(withoutAllergy.allergyWarnings()).isEmpty();
        assertThat(adapt(SkillLevel.LOW, List.of("shrimp")).allergyWarnings()).isNotEmpty();

        String conditions = jdbc.sql("SELECT conditions_json::text FROM adapted_recipe WHERE recipe_id = :id")
                .param("id", recipeId).query(String.class).single();
        String payload = jdbc.sql("SELECT payload::text FROM adapted_recipe WHERE recipe_id = :id")
                .param("id", recipeId).query(String.class).single();
        assertThat(conditions).doesNotContainIgnoringCase("shrimp").doesNotContainIgnoringCase("allerg");
        assertThat(payload).doesNotContainIgnoringCase("shrimp");
        // The stored answer has no allergy list at all, not even an ingredient's own allergens.
        AdaptationResponse stored = service.findStored(withAllergy.id());
        assertThat(stored.allergyWarnings()).isEmpty();
        assertThat(stored.allergyCheckNeededIngredientIds()).isEmpty();
        assertThat(stored.ingredients()).allSatisfy(ingredient -> assertThat(ingredient.allergens()).isEmpty());
        assertThat(stored.steps()).isEqualTo(withAllergy.steps());
    }

    @Test
    void answerNamesEveryAllergenOfAnIngredientSoTheAppCanCompareOnTheDevice() {
        AdaptationResponse response = adapt(SkillLevel.LOW, List.of());

        // Facts about the ingredient, the same whoever asks; the app compares them with the device's allergies.
        assertThat(response.ingredients().stream().filter(ingredient -> ingredient.name().equals("김치")).findFirst()
                .orElseThrow().allergens()).extracting(allergen -> allergen.code()).contains("shrimp");
        assertThat(response.ingredients()).allSatisfy(ingredient -> assertThat(ingredient.allergenReviewed()).isTrue());
        assertThat(response.allergyWarnings()).isEmpty();
    }

    @Test
    void storedAnswerIsNotUsedAfterTheSeedPromptOrModelChanged() {
        adapt(SkillLevel.LOW, List.of());
        assertThat(client.calls).isEqualTo(1);

        CacheVersions otherModel = new CacheVersions("another-model");
        CacheVersions otherSeed = new CacheVersions(versions.model()) {
            @Override
            String seedVersion() {
                return "changed-seed";
            }
        };
        assertThat(otherModel.promptVersion()).isNotEqualTo(versions.promptVersion());
        assertThat(otherModel.seedVersion()).isEqualTo(versions.seedVersion());

        serviceWith(otherModel).adapt(request(SkillLevel.LOW, List.of()));
        assertThat(client.calls).isEqualTo(2);
        serviceWith(otherSeed).adapt(request(SkillLevel.LOW, List.of()));
        assertThat(client.calls).isEqualTo(3);
        // The unchanged versions still find their own entry.
        adapt(SkillLevel.LOW, List.of());
        assertThat(client.calls).isEqualTo(3);
    }

    @Test
    void answerTheAiDidNotWordIsNotStored() {
        client.available = false;

        AdaptationResponse response = adapt(SkillLevel.LOW, List.of());

        assertThat(response.steps()).isNotEmpty();
        assertThat(response.id()).doesNotStartWith("ADAPT_");
        assertThat(rows()).isZero();
    }

    @Test
    void unknownStoredAnswerIsNotFound() {
        assertThatThrownBy(() -> service.findStored("ADAPT_99999999999999"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ADAPTATION_NOT_FOUND"));
    }

    @Test
    void everyHeatLevelHasAPictureAndAnUnknownOneFails() {
        for (String source : List.of("gas", "induction")) {
            for (HeatLevel level : HeatLevel.values()) {
                assertThat(HeatVisuals.isReady("heat." + source + "." + level.name())).isTrue();
            }
        }
        assertThat(HeatVisuals.requireKnown(null)).isNull();
        assertThat(HeatVisuals.requireKnown("heat.gas.MEDIUM")).isEqualTo("heat.gas.MEDIUM");
        // A level without a prepared picture is refused, never shown with a placeholder.
        for (String unknown : List.of("heat.gas.VERY_HIGH", "heat.charcoal.MEDIUM")) {
            assertThatThrownBy(() -> HeatVisuals.requireKnown(unknown))
                    .isInstanceOfSatisfying(ApiException.class,
                            exception -> assertThat(exception.code()).isEqualTo("UNKNOWN_HEAT_VISUAL"));
        }
        assertThatThrownBy(() -> HeatVisuals.requireKnown("heat.gas.MEDIUM", Set.of("heat.gas.LOW")))
                .isInstanceOf(ApiException.class);
        // Every adapted step of the fixture names a picture that exists.
        List<AdaptedStep> steps = adapt(SkillLevel.LOW, List.of()).steps();
        assertThat(steps.stream().map(AdaptedStep::heatVisualKey).filter(key -> key != null))
                .isNotEmpty().allMatch(HeatVisuals::isReady);
    }

    private AdaptationService serviceWith(CacheVersions cacheVersions) {
        return new AdaptationService(recipes, reference, measureConverter, new StepRewriter(client, reference), cache,
                cacheVersions);
    }

    private AdaptationResponse adapt(SkillLevel skillLevel, List<String> allergenCodes) {
        return service.adapt(request(skillLevel, allergenCodes));
    }

    private AdaptationRequest request(SkillLevel skillLevel, List<String> allergenCodes) {
        return new AdaptationRequest(recipeId, 1, List.of(), List.of(),
                new Profile(skillLevel, TOOLS, null, allergenCodes));
    }

    private long rows() {
        return jdbc.sql("SELECT count(*) FROM adapted_recipe WHERE recipe_id = :id").param("id", recipeId)
                .query(Long.class).single();
    }

    /** Answers every step with its own sentence as one part and counts how often it is asked. */
    private static class CountingClient extends OpenAiClient {

        private boolean available = true;
        private int calls;

        CountingClient() {
            super("", "test-model", 0, 1, null);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<String> complete(Purpose purpose, String promptName, Object input) {
            calls++;
            List<Map<String, Object>> answers = new ArrayList<>();
            for (Map<String, Object> step : (List<Map<String, Object>>) ((Map<String, Object>) input).get("steps")) {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("action", step.get("action"));
                part.put("actionKeys", List.of(step.get("actionKey") == null ? "action.other" : step.get("actionKey")));
                part.put("details", null);
                Map<String, Object> answer = new LinkedHashMap<>();
                answer.put("stepId", step.get("stepId"));
                answer.put("parts", List.of(part));
                answers.add(answer);
            }
            return Optional.of(Json.write(Map.of("steps", answers)));
        }
    }
}
