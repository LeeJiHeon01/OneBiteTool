package com.example.aicook.recipeimport;

import com.example.aicook.ai.OpenAiClient;
import com.example.aicook.ai.OpenAiClient.Purpose;
import com.example.aicook.common.ApiException;
import com.example.aicook.common.Json;
import com.example.aicook.recipe.RecipeRepository;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Tools.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Brings a dish that is not in the database in from the web (spec 6.3): the AI finds real recipe
 * pages, the server downloads one, the AI extracts ingredients and steps from that text only,
 * and the server stores the result after checking every value against the page.
 */
@Service
public class RecipeImportService {

    public enum Status {
        PENDING,
        SEARCHING,
        EXTRACTING,
        VERIFYING,
        DONE,
        NOT_FOUND,
        FAILED
    }

    public record ImportRequest(String query, List<String> toolVariantIds) {
    }

    public record ImportJob(String id, Status status, String recipeId) {
    }

    private static final Logger log = LoggerFactory.getLogger(RecipeImportService.class);
    private static final int MAX_QUERY_LENGTH = 40;
    /** Each candidate costs one extraction call. */
    private static final int MAX_CANDIDATES = 3;
    private static final int MAX_NAME_LENGTH = 128;
    /** More steps than this many times the page's own count means the extract split too finely. */
    private static final double MAX_STEP_RATIO = 1.5;

    private final JdbcClient jdbc;
    private final OpenAiClient client;
    private final PageFetcher pageFetcher;
    private final ImportedRecipeWriter writer;
    private final ReferenceData reference;
    private final ExtractValidator validator;
    /** Sites whose pages are tried first (spec Q-B4), e.g. 10000recipe.com. */
    private final List<String> preferredSites;

    public RecipeImportService(JdbcClient jdbc, OpenAiClient client, PageFetcher pageFetcher,
                               ImportedRecipeWriter writer, ReferenceData reference,
                               @Value("${app.preferred-recipe-sites}") List<String> preferredSites) {
        this.preferredSites = preferredSites;
        this.jdbc = jdbc;
        this.client = client;
        this.pageFetcher = pageFetcher;
        this.writer = writer;
        this.reference = reference;
        this.validator = new ExtractValidator(reference);
    }

    /** Starts an import in the background, or returns the job that already covers the same dish. */
    public ImportJob start(ImportRequest request) {
        String query = request.query() == null ? "" : request.query().strip();
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            throw ApiException.badRequest("INVALID_QUERY", "요리 이름을 " + MAX_QUERY_LENGTH + "자 안으로 입력해 주세요.");
        }
        List<String> ownedTools = ownedToolNames(request.toolVariantIds());
        Optional<ImportJob> existing = findReusable(RecipeRepository.normalize(query));
        if (existing.isPresent()) {
            return existing.get();
        }
        if (client.isDailyLimitReached()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DAILY_LIMIT",
                    "오늘은 새 레시피를 더 찾을 수 없어요. 내일 다시 시도해 주세요.");
        }
        if (!client.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                    "지금은 새 레시피를 찾을 수 없어요. 잠시 후 다시 시도해 주세요.");
        }
        String jobId = jdbc.sql("""
                        INSERT INTO recipe_import_job (query_text, normalized_query) VALUES (:query, :normalized)
                        RETURNING id
                        """)
                .param("query", query)
                .param("normalized", RecipeRepository.normalize(query))
                .query(String.class).single();
        Thread.ofVirtual().name("recipe-import-" + jobId).start(() -> run(jobId, query, ownedTools));
        return new ImportJob(jobId, Status.PENDING, null);
    }

    public ImportJob find(String jobId) {
        return jdbc.sql("SELECT id, status, recipe_id FROM recipe_import_job WHERE id = :id")
                .param("id", jobId)
                .query((rs, row) -> new ImportJob(rs.getString("id"), Status.valueOf(rs.getString("status")),
                        rs.getString("recipe_id")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("IMPORT_NOT_FOUND", "가져오기 작업을 찾지 못했어요."));
    }

    /** Runs the whole import for one job; never throws, the outcome is written to the job row. */
    void run(String jobId, String dishName, List<String> ownedTools) {
        try {
            Optional<String> recipeId = importRecipe(jobId, dishName, ownedTools);
            finish(jobId, recipeId.isPresent() ? Status.DONE : Status.NOT_FOUND, recipeId.orElse(null), null);
        } catch (RuntimeException exception) {
            log.warn("Recipe import {} failed: {}", jobId, exception.getClass().getSimpleName());
            finish(jobId, Status.FAILED, null, exception.getClass().getSimpleName());
        }
    }

    private Optional<String> importRecipe(String jobId, String dishName, List<String> ownedTools) {
        setStatus(jobId, Status.SEARCHING);
        for (Extract.Candidate candidate : search(dishName, ownedTools)) {
            Optional<String> stored = writer.findByUrl(candidate.url());
            if (stored.isPresent()) {
                return stored;
            }
            setStatus(jobId, Status.EXTRACTING);
            Optional<String> pageText = pageFetcher.fetchText(candidate.url());
            Optional<Extract.Page> page = pageText.flatMap(text -> extract(dishName, candidate.url(), text));
            if (page.isEmpty()) {
                noteRejection(jobId, pageText.isEmpty() ? "PAGE_UNAVAILABLE" : "EXTRACT_FAILED");
                continue;
            }
            setStatus(jobId, Status.VERIFYING);
            Optional<String> violation = validator.firstViolation(page.get(), pageText.get());
            if (violation.isPresent()) {
                noteRejection(jobId, violation.get());
                continue;
            }
            warnIfOverSplit(jobId, page.get());
            return Optional.of(save(dishName, candidate.url(), page.get(), pageText.get()));
        }
        return Optional.empty();
    }

    /** Steps follow the page's own step boundaries; far more steps than the page has is reported. */
    private void warnIfOverSplit(String reference, Extract.Page page) {
        Integer sourceSteps = page.sourceStepCount();
        if (sourceSteps != null && sourceSteps > 0 && page.steps().size() > sourceSteps * MAX_STEP_RATIO) {
            log.warn("Recipe import {}: {} steps extracted from {} source steps", reference, page.steps().size(),
                    sourceSteps);
        }
    }

    public record Reextracted(String name, String oldRecipeId, String newRecipeId, int stepsBefore,
                              Integer stepsAfter, Integer sourceStepCount, String rejectedBecause) {
    }

    /**
     * Extracts a stored web recipe again from the same page and replaces it when the new extract
     * passes the same checks. The stored recipe is kept when the page is gone or the check fails.
     */
    public Reextracted reextract(String recipeId) {
        record Stored(String name, String url, int steps) {
        }
        Stored stored = jdbc.sql("""
                        SELECT r.name, r.source_url,
                               (SELECT count(*) FROM recipe_step s WHERE s.recipe_id = r.id) AS steps
                        FROM recipe r WHERE r.id = :id AND r.source = 'WEB_SEARCH'
                        """)
                .param("id", recipeId)
                .query((rs, row) -> new Stored(rs.getString("name"), rs.getString("source_url"), rs.getInt("steps")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("RECIPE_NOT_FOUND", "웹에서 가져온 레시피가 아니에요."));
        Optional<String> pageText = pageFetcher.fetchText(stored.url());
        Optional<Extract.Page> page = pageText.flatMap(text -> extract(stored.name(), stored.url(), text));
        if (page.isEmpty()) {
            return new Reextracted(stored.name(), recipeId, null, stored.steps(), null, null,
                    pageText.isEmpty() ? "PAGE_UNAVAILABLE" : "EXTRACT_FAILED");
        }
        Optional<String> violation = validator.firstViolation(page.get(), pageText.get());
        if (violation.isPresent()) {
            return new Reextracted(stored.name(), recipeId, null, stored.steps(), page.get().steps().size(),
                    page.get().sourceStepCount(), violation.get());
        }
        warnIfOverSplit(recipeId, page.get());
        String compactPage = ExtractValidator.compact(pageText.get());
        Double servings = ExtractValidator.servingsQuoted(page.get(), pageText.get()) ? page.get().servings() : null;
        Integer totalMinutes = page.get().totalMinutes() != null && page.get().totalMinutes() > 0
                && compactPage.contains(String.valueOf(page.get().totalMinutes())) ? page.get().totalMinutes() : null;
        String newId = writer.replace(recipeId, stored.name(), stored.url(), servings, totalMinutes, page.get());
        return new Reextracted(stored.name(), recipeId, newId, stored.steps(), page.get().steps().size(),
                page.get().sourceStepCount(), null);
    }

    /** Candidate pages: the ones that fit the user's tools first, preferred sites first among those. Only well-formed, distinct addresses. */
    private List<Extract.Candidate> search(String dishName, List<String> ownedTools) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("dishName", dishName);
        input.put("ownedTools", ownedTools);
        input.put("preferredSites", preferredSites);
        List<Extract.Candidate> candidates = client.completeWithWebSearch(Purpose.WEB_SEARCH, "recipe-search", input)
                .map(answer -> Json.parse(answer, Extract.SearchAnswer.class).candidates())
                .orElse(List.of());
        Map<String, Extract.Candidate> byUrl = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> isHttpUrl(candidate.url()))
                // Fitting the user's tools comes first, then the preferred sites.
                .sorted(Comparator.comparing(Extract.Candidate::needsOtherTools)
                        .thenComparing(candidate -> !isPreferredSite(candidate.url())))
                .forEach(candidate -> byUrl.putIfAbsent(candidate.url(), candidate));
        return byUrl.values().stream().limit(MAX_CANDIDATES).toList();
    }

    private Optional<Extract.Page> extract(String dishName, String url, String pageText) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("dishName", dishName);
        input.put("pageUrl", url);
        input.put("pageText", pageText);
        return client.complete(Purpose.EXTRACT, "recipe-extract", input)
                .map(answer -> Json.parse(answer, Extract.Page.class));
    }

    private String save(String dishName, String url, Extract.Page page, String pageText) {
        String compactPage = ExtractValidator.compact(pageText);
        // The page's own title is used only when it really is on the page.
        boolean nameOnPage = page.name() != null && !page.name().isBlank()
                && page.name().length() <= MAX_NAME_LENGTH
                && compactPage.contains(ExtractValidator.compact(page.name()));
        Double servings = ExtractValidator.servingsQuoted(page, pageText) ? page.servings() : null;
        // The total time has no quote to check, so it is kept only when the number is on the page.
        Integer totalMinutes = page.totalMinutes() != null && page.totalMinutes() > 0
                && compactPage.contains(String.valueOf(page.totalMinutes())) ? page.totalMinutes() : null;
        return writer.save(nameOnPage ? page.name().strip() : dishName, url, servings, totalMinutes, page);
    }

    /** Names of the user's cooking tools; spoons, cups and bowls say nothing about which recipe fits. */
    private List<String> ownedToolNames(List<String> toolVariantIds) {
        if (toolVariantIds == null) {
            return List.of();
        }
        return toolVariantIds.stream()
                .map(id -> reference.tool(id)
                        .orElseThrow(() -> ApiException.badRequest("UNKNOWN_TOOL", "알 수 없는 도구예요.")))
                .filter(tool -> !tool.isMeasuringTool() && !tool.isStandardMeasure())
                .map(Variant::name)
                .toList();
    }

    /** A finished import of the same dish, or one that is still running (started in the last 5 minutes). */
    private Optional<ImportJob> findReusable(String normalizedQuery) {
        return jdbc.sql("""
                        SELECT id, status, recipe_id FROM recipe_import_job
                        WHERE normalized_query = :query
                          AND ((status = 'DONE' AND recipe_id IS NOT NULL)
                               OR (status IN ('PENDING', 'SEARCHING', 'EXTRACTING', 'VERIFYING')
                                   AND created_at > now() - interval '5 minutes'))
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .param("query", normalizedQuery)
                .query((rs, row) -> new ImportJob(rs.getString("id"), Status.valueOf(rs.getString("status")),
                        rs.getString("recipe_id")))
                .optional();
    }

    /** Why a candidate was dropped is kept on the job (a short code only, never page text). */
    private void noteRejection(String jobId, String code) {
        log.info("Recipe import {}: candidate rejected ({})", jobId, code);
        jdbc.sql("UPDATE recipe_import_job SET error_code = left(concat_ws(',', error_code, :code), 64) WHERE id = :id")
                .param("code", code).param("id", jobId).update();
    }

    private void setStatus(String jobId, Status status) {
        jdbc.sql("UPDATE recipe_import_job SET status = :status WHERE id = :id")
                .param("status", status.name()).param("id", jobId).update();
    }

    private void finish(String jobId, Status status, String recipeId, String errorCode) {
        jdbc.sql("""
                        UPDATE recipe_import_job
                        SET status = :status, recipe_id = :recipeId, error_code = COALESCE(:errorCode, error_code)
                        WHERE id = :id
                        """)
                .param("status", status.name()).param("recipeId", recipeId).param("errorCode", errorCode)
                .param("id", jobId).update();
    }

    private boolean isPreferredSite(String url) {
        String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
        return preferredSites.stream().anyMatch(site -> host.equals(site) || host.endsWith("." + site));
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
