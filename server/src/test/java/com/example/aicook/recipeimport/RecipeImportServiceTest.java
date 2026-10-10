package com.example.aicook.recipeimport;

import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.common.ApiException;
import com.example.aicook.recipe.Recipe;
import com.example.aicook.recipe.RecipeService;
import com.example.aicook.recipeimport.RecipeImportService.ImportJob;
import com.example.aicook.recipeimport.RecipeImportService.ImportRequest;
import com.example.aicook.recipeimport.RecipeImportService.Status;
import com.example.aicook.reference.ReferenceData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Web import (spec 6.3) with prepared AI answers and page text: no real API or network calls.
 * Only values quoted from the page may be stored.
 */
// The API key is cleared so tests never call the AI API.
@SpringBootTest(properties = {"spring.datasource.url=${SPRING_DATASOURCE_TEST_URL}", "openai.api-key="})
@ExtendWith(OutputCaptureExtension.class)
class RecipeImportServiceTest {

    private static final String DISH = "테스트된장찌개";
    private static final String URL_A = "https://recipes.example.com/a";
    private static final String URL_B = "https://www.recipes.example.com/b";
    private static final String PAGE = """
            테스트된장찌개 2인분 재료 된장 2큰술 두부 1/2모 소금 약간
            만드는 법 냄비에 물을 붓고 된장을 풀어 줍니다. 두부를 넣고 중불에서 5분 끓입니다.
            """;
    private static final String SEARCH = """
            {"candidates":[
              {"url":"ftp://recipes.example.com/x","title":"x","reason":"x","needsOtherTools":false},
              {"url":"%s","title":"a","reason":"a","needsOtherTools":false},
              {"url":"%s","title":"b","reason":"b","needsOtherTools":false}]}
            """.formatted(URL_A, URL_B);

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ImportedRecipeWriter writer;
    @Autowired
    private ReferenceData reference;
    @Autowired
    private RecipeService recipes;

    private final FakeClient client = new FakeClient();
    private final List<String> fetched = new ArrayList<>();
    private RecipeImportService service;

    @BeforeEach
    void setUp() {
        cleanUp();
        PageFetcher fetcher = new PageFetcher() {
            @Override
            public Optional<String> fetchText(String url) {
                fetched.add(url);
                return Optional.of(PAGE);
            }
        };
        service = new RecipeImportService(jdbc, client, fetcher, writer, reference, List.of("recipes.example.com"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM recipe_import_job WHERE query_text = :dish").param("dish", DISH).update();
        jdbc.sql("DELETE FROM recipe WHERE source = 'WEB_SEARCH' AND source_domain = 'recipes.example.com'").update();
    }

    @Test
    void checkedExtractIsStoredAsWebSearchRecipe() {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));

        ImportJob job = runImport();

        assertThat(job.status()).isEqualTo(Status.DONE);
        Recipe recipe = recipes.find(job.recipeId());
        assertThat(recipe.source()).isEqualTo(Recipe.Source.WEB_SEARCH);
        assertThat(recipe.verificationStatus()).isEqualTo(Recipe.VerificationStatus.IMPORTED);
        assertThat(recipe.sourceUrl()).isEqualTo(URL_A);
        assertThat(recipe.sourceDomain()).isEqualTo("recipes.example.com");
        assertThat(recipe.baseServings()).isEqualTo(2.0);
        assertThat(recipe.ingredients()).extracting(Recipe.Ingredient::rawText)
                .containsExactly("된장 2큰술", "두부 1/2모", "소금 약간");
        // "약간" is never turned into a number.
        assertThat(recipe.ingredients().get(2).unitKind()).isEqualTo(Recipe.UnitKind.VAGUE);
        assertThat(recipe.ingredients().get(2).quantity()).isNull();
        Recipe.Step boil = recipe.steps().get(1);
        assertThat(boil.durationSeconds()).isEqualTo(300);
        assertThat(boil.heatLevel()).isEqualTo(Recipe.HeatLevel.MEDIUM);
        assertThat(boil.toolIds()).containsExactly("pot");
        // The first step has no time on the page, so none is stored.
        assertThat(recipe.steps().get(0).durationSeconds()).isNull();
        // The unsafe address was never requested.
        assertThat(fetched).containsExactly(URL_A);
    }

    @Test
    void candidateWithTimeThatIsNotOnThePageIsSkipped() {
        client.answers.add(SEARCH);
        client.answers.add(extract(600, "중불에서 5분 끓입니다"));
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));

        ImportJob job = runImport();

        assertThat(job.status()).isEqualTo(Status.DONE);
        assertThat(recipes.find(job.recipeId()).sourceUrl()).isEqualTo(URL_B);
    }

    @Test
    void nothingIsStoredWhenEveryCandidateFailsTheCheck() {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "약불에서 5분 끓입니다"));
        client.answers.add(extract(300, "중불에서 5분 끓입니다").replace("된장 2큰술", "된장 3큰술"));

        ImportJob job = runImport();

        assertThat(job.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(job.recipeId()).isNull();
        assertThat(importedRecipeCount()).isZero();
    }

    @Test
    void preferredSiteIsTriedBeforeOtherSitesButAfterToolFit() {
        String preferred = "https://www.recipes.example.com/c";
        client.answers.add("""
                {"candidates":[
                  {"url":"https://other.example.org/1","title":"o","reason":"o","needsOtherTools":false},
                  {"url":"https://recipes.example.com/oven","title":"p","reason":"p","needsOtherTools":true},
                  {"url":"%s","title":"c","reason":"c","needsOtherTools":false}]}
                """.formatted(preferred));
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));

        ImportJob job = runImport();

        assertThat(fetched).containsExactly(preferred);
        assertThat(recipes.find(job.recipeId()).sourceUrl()).isEqualTo(preferred);
    }

    @Test
    void sizeFromThePageMayStayInTheSentenceButAmountsAndInventedNumbersMayNot() {
        String valid = extract(300, "중불에서 5분 끓입니다");
        client.answers.add(SEARCH);
        client.answers.add(valid.replace("두부를 넣고 끓여요", "두부를 넣고 5분 끓여요"));
        client.answers.add(valid.replace("두부를 넣고 끓여요", "두부를 2cm로 썰어 넣어요"));

        assertThat(runImport().status()).isEqualTo(Status.NOT_FOUND);
        assertThat(jdbc.sql("SELECT error_code FROM recipe_import_job WHERE query_text = :dish")
                .param("dish", DISH).query(String.class).single())
                .isEqualTo("AMOUNT_IN_ACTION,NUMBER_NOT_IN_PAGE");
    }

    @Test
    void numberQuotedFromThePageThatIsNotAnAmountIsKept() {
        client.answers.add(SEARCH);
        // "5" is in the step's quote; "번" is not an amount, time or temperature unit.
        client.answers.add(extract(300, "중불에서 5분 끓입니다").replace("두부를 넣고 끓여요", "두부를 넣고 5번 저어요"));

        assertThat(runImport().status()).isEqualTo(Status.DONE);
    }

    @Test
    void sentenceCopiedFromThePageIsNotStored() {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "중불에서 5분 끓입니다")
                .replace("냄비에 물을 붓고 된장을 풀어요", "냄비에 물을 붓고 된장을 풀어 줍니다."));
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));

        ImportJob job = runImport();

        assertThat(job.status()).isEqualTo(Status.DONE);
        assertThat(recipes.find(job.recipeId()).sourceUrl()).isEqualTo(URL_B);
    }

    @Test
    void manyMoreStepsThanThePageHasIsLoggedAsAWarning(CapturedOutput output) {
        client.answers.add(SEARCH);
        // The page has one numbered step; two were extracted (more than 1.5 times).
        client.answers.add(extract(300, "중불에서 5분 끓입니다").replace("\"isRecipe\":true,",
                "\"isRecipe\":true,\"sourceStepCount\":1,"));

        assertThat(runImport().status()).isEqualTo(Status.DONE);
        assertThat(output.getOut()).contains("2 steps extracted from 1 source steps");
    }

    @Test
    void stepsWithinThePagesOwnCountAreNotReported(CapturedOutput output) {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "중불에서 5분 끓입니다").replace("\"isRecipe\":true,",
                "\"isRecipe\":true,\"sourceStepCount\":2,"));

        assertThat(runImport().status()).isEqualTo(Status.DONE);
        assertThat(output.getOut()).doesNotContain("steps extracted from");
    }

    @Test
    void storedRecipeIsReplacedByANewExtractOfTheSamePage() {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));
        ImportJob job = runImport();
        // The new extract keeps the page's two steps in one, as the page has them.
        client.answers.add(extract(300, "중불에서 5분 끓입니다")
                .replace("\"isRecipe\":true,", "\"isRecipe\":true,\"sourceStepCount\":2,")
                .replace("두부를 넣고 끓여요", "두부를 넣고 뭉근히 끓여요"));

        RecipeImportService.Reextracted result = service.reextract(job.recipeId());

        assertThat(result.rejectedBecause()).isNull();
        assertThat(result.stepsBefore()).isEqualTo(2);
        assertThat(result.stepsAfter()).isEqualTo(2);
        assertThat(result.newRecipeId()).isNotEqualTo(job.recipeId());
        assertThat(recipes.find(result.newRecipeId()).steps().get(1).action()).isEqualTo("두부를 넣고 뭉근히 끓여요");
        assertThat(recipes.find(result.newRecipeId()).sourceUrl()).isEqualTo(URL_A);
        // The same search keeps finding the recipe.
        assertThat(service.find(job.id()).recipeId()).isEqualTo(result.newRecipeId());
        assertThat(importedRecipeCount()).isEqualTo(1);
    }

    @Test
    void storedRecipeIsKeptWhenTheNewExtractFailsTheCheck() {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));
        ImportJob job = runImport();
        client.answers.add(extract(600, "중불에서 5분 끓입니다"));

        RecipeImportService.Reextracted result = service.reextract(job.recipeId());

        assertThat(result.rejectedBecause()).isEqualTo("TIME_NOT_IN_PAGE");
        assertThat(result.newRecipeId()).isNull();
        assertThat(recipes.find(job.recipeId()).steps()).hasSize(2);
    }

    @Test
    void noCandidatesMeansNotFound() {
        client.answers.add("{\"candidates\":[]}");

        assertThat(runImport().status()).isEqualTo(Status.NOT_FOUND);
        assertThat(fetched).isEmpty();
    }

    @Test
    void sameDishReusesTheFinishedImport() {
        client.answers.add(SEARCH);
        client.answers.add(extract(300, "중불에서 5분 끓입니다"));
        ImportJob first = runImport();
        int callsAfterFirst = client.calls;

        ImportJob second = service.start(new ImportRequest(" " + DISH + " ", List.of("pot")));

        assertThat(second).isEqualTo(first);
        assertThat(client.calls).isEqualTo(callsAfterFirst);
    }

    @Test
    void unavailableAiAndBadRequestsAreRefused() {
        client.available = false;
        assertThatThrownBy(() -> service.start(new ImportRequest(DISH, List.of())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("AI_UNAVAILABLE");
                });
        client.available = true;
        // Today's calls are used up: a different code, so the app can say "try again tomorrow".
        client.dailyLimitReached = true;
        assertThatThrownBy(() -> service.start(new ImportRequest(DISH, List.of())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("AI_DAILY_LIMIT");
                });
        client.dailyLimitReached = false;
        assertThatThrownBy(() -> service.start(new ImportRequest("  ", List.of())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_QUERY"));
        assertThatThrownBy(() -> service.start(new ImportRequest(DISH, List.of("no_such_tool"))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("UNKNOWN_TOOL"));
        assertThat(client.calls).isZero();
    }

    @Test
    void onlyCookingToolNamesAreSentToTheSearch() {
        client.answers.add("{\"candidates\":[]}");

        service.start(new ImportRequest(DISH, List.of("pot", "spoon_rice", "measuring_cup", "gas_stove")));
        waitUntilFinished();

        assertThat(client.inputs.get(0)).containsEntry("dishName", DISH)
                .containsEntry("ownedTools", List.of("냄비", "가스레인지"))
                .containsEntry("preferredSites", List.of("recipes.example.com"));
    }

    @Test
    void localAndPrivateAddressesAreNeverFetched() throws Exception {
        for (String url : List.of("http://127.0.0.1/a", "http://localhost:8080/a", "http://10.0.0.5/a",
                "http://192.168.0.1/a", "http://169.254.169.254/latest", "http://[::1]/a", "file:///etc/hosts",
                "ftp://93.184.216.34/a")) {
            assertThat(PageFetcher.isPublicHttp(URI.create(url))).as(url).isFalse();
        }
        assertThat(PageFetcher.isPublicHttp(URI.create("https://93.184.216.34/a"))).isTrue();
    }

    private ImportJob runImport() {
        service.start(new ImportRequest(DISH, List.of("pot", "gas_stove")));
        return waitUntilFinished();
    }

    private ImportJob waitUntilFinished() {
        String jobId = jdbc.sql("SELECT id FROM recipe_import_job WHERE query_text = :dish ORDER BY id DESC LIMIT 1")
                .param("dish", DISH).query(String.class).single();
        for (int attempt = 0; attempt < 100; attempt++) {
            ImportJob job = service.find(jobId);
            if (List.of(Status.DONE, Status.NOT_FOUND, Status.FAILED).contains(job.status())) {
                return job;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("import did not finish");
    }

    private int importedRecipeCount() {
        return jdbc.sql("SELECT count(*) FROM recipe WHERE source_domain = 'recipes.example.com'")
                .query(Integer.class).single();
    }

    /** An extract of PAGE; the boiling step's time and its quote are the parts the tests vary. */
    private static String extract(int boilSeconds, String boilEvidence) {
        return """
                {"isRecipe":true,"name":"테스트된장찌개","servings":2,"servingsEvidence":"2인분","totalMinutes":null,
                 "ingredients":[
                  {"name":"된장","amountText":"2큰술","section":null,"evidence":"된장 2큰술"},
                  {"name":"두부","amountText":"1/2모","section":null,"evidence":"두부 1/2모"},
                  {"name":"소금","amountText":"약간","section":null,"evidence":"소금 약간"}],
                 "steps":[
                  {"action":"냄비에 물을 붓고 된장을 풀어요","actionKey":"action.mix","ingredientNames":["된장"],
                   "toolCodes":["pot"],"durationSeconds":null,"durationEvidence":null,"timerStartCondition":null,
                   "heatLevel":null,"heatRaw":null,"deviceSettingRaw":null,"completionCheck":null,
                   "evidence":"냄비에 물을 붓고 된장을 풀어 줍니다"},
                  {"action":"두부를 넣고 끓여요","actionKey":"action.boil","ingredientNames":["두부"],
                   "toolCodes":["pot"],"durationSeconds":%d,"durationEvidence":"%s",
                   "timerStartCondition":"끓기 시작하면","heatLevel":"MEDIUM","heatRaw":"중불",
                   "deviceSettingRaw":null,"completionCheck":null,"evidence":"두부를 넣고 중불에서 5분 끓입니다"}]}
                """.formatted(boilSeconds, boilEvidence);
    }

    /** Returns the prepared answers in order and records what was sent. */
    private static class FakeClient extends OpenAiClient {

        private final Deque<String> answers = new ArrayDeque<>();
        private final List<Map<String, Object>> inputs = new ArrayList<>();
        private boolean available = true;
        private boolean dailyLimitReached;
        private int calls;

        FakeClient() {
            super("", "test-model", 0, 1, null);
        }

        @Override
        public boolean isAvailable() {
            return available && !dailyLimitReached;
        }

        @Override
        public boolean isDailyLimitReached() {
            return dailyLimitReached;
        }

        @Override
        public Optional<String> complete(Purpose purpose, String promptName, Object input) {
            return next(input);
        }

        @Override
        public Optional<String> completeWithWebSearch(Purpose purpose, String promptName, Object input) {
            return next(input);
        }

        @SuppressWarnings("unchecked")
        private Optional<String> next(Object input) {
            calls++;
            inputs.add((Map<String, Object>) input);
            return Optional.ofNullable(answers.poll());
        }
    }
}
