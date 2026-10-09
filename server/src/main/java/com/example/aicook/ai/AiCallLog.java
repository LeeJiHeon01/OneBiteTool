package com.example.aicook.ai;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Call counts and cost data of AI requests. Prompt and response text are never stored. */
@Repository
public class AiCallLog {

    private final JdbcClient jdbc;

    public AiCallLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Calls made since midnight (database time), failed ones included. */
    public int countToday() {
        return jdbc.sql("SELECT count(*) FROM ai_call_log WHERE created_at >= date_trunc('day', now())")
                .query(Integer.class)
                .single();
    }

    public void record(String purpose, String model, Integer inputTokens, Integer outputTokens, long latencyMs,
                       String errorCode) {
        jdbc.sql("""
                        INSERT INTO ai_call_log (purpose, model, input_tokens, output_tokens, latency_ms, success,
                                                 error_code)
                        VALUES (:purpose, :model, :inputTokens, :outputTokens, :latencyMs, :success, :errorCode)
                        """)
                .param("purpose", purpose)
                .param("model", model)
                .param("inputTokens", inputTokens)
                .param("outputTokens", outputTokens)
                .param("latencyMs", (int) latencyMs)
                .param("success", errorCode == null)
                .param("errorCode", errorCode)
                .update();
    }
}
