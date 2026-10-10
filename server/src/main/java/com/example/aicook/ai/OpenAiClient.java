package com.example.aicook.ai;

import com.example.aicook.common.Json;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Calls the OpenAI Responses API with a system prompt and a strict JSON schema (Structured
 * Outputs). The API key stays on the server. Every call is counted against the daily limit and
 * written to ai_call_log; prompt and response text are never logged.
 */
@Component
public class OpenAiClient {

    public enum Purpose {
        WEB_SEARCH,
        EXTRACT,
        ADAPT,
        QUESTION
    }

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");

    private final String apiKey;
    private final String model;
    private final int dailyCallLimit;
    private final Duration timeout;
    private final AiCallLog callLog;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiClient(@Value("${openai.api-key:}") String apiKey, @Value("${openai.model}") String model,
                        @Value("${openai.daily-call-limit}") int dailyCallLimit,
                        @Value("${openai.timeout-seconds}") int timeoutSeconds, AiCallLog callLog) {
        this.apiKey = apiKey;
        this.model = model;
        this.dailyCallLimit = dailyCallLimit;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.callLog = callLog;
    }

    /** False without an API key or once today's call limit is used up. */
    public boolean isAvailable() {
        if (apiKey.isBlank()) {
            return false;
        }
        if (isDailyLimitReached()) {
            log.warn("Daily AI call limit of {} reached", dailyCallLimit);
            return false;
        }
        return true;
    }

    /** True when a key is configured but today's calls are used up: the AI works again tomorrow. */
    public boolean isDailyLimitReached() {
        return !apiKey.isBlank() && callLog.countToday() >= dailyCallLimit;
    }

    /**
     * Sends {@code input} as JSON and returns the model's JSON answer.
     *
     * @param promptName file name under prompts/ and prompts/schemas/ without extension
     * @return empty when the call is not available, fails, times out or is refused
     */
    public Optional<String> complete(Purpose purpose, String promptName, Object input) {
        return call(purpose, promptName, input, false);
    }

    /** Same as {@link #complete}, with the model allowed to use OpenAI's web search tool. */
    public Optional<String> completeWithWebSearch(Purpose purpose, String promptName, Object input) {
        return call(purpose, promptName, input, true);
    }

    private Optional<String> call(Purpose purpose, String promptName, Object input, boolean webSearch) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        long startedAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response = http.send(request(promptName, input, webSearch),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = System.currentTimeMillis() - startedAt;
            if (response.statusCode() != 200) {
                return failed(purpose, latencyMs, "HTTP_" + response.statusCode());
            }
            Response answer = Json.parse(response.body(), Response.class);
            String text = answer.text();
            if (text == null || text.isBlank()) {
                return failed(purpose, latencyMs, "NO_CONTENT");
            }
            callLog.record(purpose.name(), model, answer.inputTokens(), answer.outputTokens(), latencyMs, null);
            return Optional.of(text);
        } catch (IOException | RuntimeException exception) {
            return failed(purpose, System.currentTimeMillis() - startedAt, exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(purpose, System.currentTimeMillis() - startedAt, "INTERRUPTED");
        }
    }

    private HttpRequest request(String promptName, Object input, boolean webSearch) {
        // The schema file already has the {name, strict, schema} shape of a json_schema format.
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.putAll(Json.readResource("prompts/schemas/" + promptName + ".json", SchemaFile.class).asMap());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", Json.readText("prompts/" + promptName + ".md"));
        body.put("input", Json.write(input));
        body.put("text", Map.of("format", format));
        if (webSearch) {
            body.put("tools", List.of(Map.of("type", "web_search")));
        }
        return HttpRequest.newBuilder(ENDPOINT)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
    }

    private Optional<String> failed(Purpose purpose, long latencyMs, String errorCode) {
        log.warn("AI call failed: purpose={}, error={}", purpose, errorCode);
        callLog.record(purpose.name(), model, null, null, latencyMs, errorCode);
        return Optional.empty();
    }

    record SchemaFile(String name, boolean strict, Map<String, Object> schema) {

        Map<String, Object> asMap() {
            return Map.of("name", name, "strict", strict, "schema", schema);
        }
    }

    /** The parts of a Responses API answer the server reads. */
    record Response(List<Output> output, Usage usage) {

        /** Text of the assistant message; tool calls and reasoning items carry none. */
        String text() {
            if (output == null) {
                return null;
            }
            return output.stream()
                    .filter(item -> "message".equals(item.type()) && item.content() != null)
                    .flatMap(item -> item.content().stream())
                    .filter(content -> "output_text".equals(content.type()))
                    .map(Content::text)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        Integer inputTokens() {
            return usage == null ? null : usage.inputTokens();
        }

        Integer outputTokens() {
            return usage == null ? null : usage.outputTokens();
        }
    }

    record Output(String type, List<Content> content) {
    }

    record Content(String type, String text) {
    }

    record Usage(@JsonProperty("input_tokens") Integer inputTokens,
                 @JsonProperty("output_tokens") Integer outputTokens) {
    }
}
