package com.example.aicook.adaptation;

import com.example.aicook.common.Json;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Stored adaptations (adapted_recipe): the same recipe under the same conditions is answered
 * without asking the AI again. Neither the conditions nor the stored answer hold anything about
 * the user's allergies; allergy warnings are worked out for each request and never stored.
 */
@Repository
class AdaptationCache {

    /**
     * id is the adapted_recipe id; response is the stored answer without any allergy lists.
     * ingredientCodes maps each ingredient of the answer to its dictionary code (the substitute's
     * when one was used), so what the ingredient contains can be looked up when the answer is read.
     */
    record Entry(String id, AdaptationResponse response, Map<String, String> ingredientCodes) {
    }

    /** Key in the stored conditions under which the dictionary codes of the ingredients are kept. */
    static final String INGREDIENT_CODES = "ingredientCodes";

    private record StoredConditions(Map<String, String> ingredientCodes) {
    }

    private final JdbcClient jdbc;

    AdaptationCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** SHA-256 of the conditions as JSON, the lookup key next to the recipe id. */
    static String hashOf(Map<String, Object> conditions) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Json.write(conditions).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    Optional<Entry> find(String recipeId, String conditionsHash) {
        return jdbc.sql("""
                        SELECT id, payload, conditions_json FROM adapted_recipe
                        WHERE recipe_id = :recipeId AND conditions_hash = :hash
                        """)
                .param("recipeId", recipeId)
                .param("hash", conditionsHash)
                .query((rs, row) -> entry(rs.getString("id"), rs.getString("payload"), rs.getString("conditions_json")))
                .optional();
    }

    Optional<Entry> findById(String id) {
        return jdbc.sql("SELECT id, payload, conditions_json FROM adapted_recipe WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> entry(rs.getString("id"), rs.getString("payload"), rs.getString("conditions_json")))
                .optional();
    }

    /**
     * Stores an answer and returns its id. The caller passes an answer with empty allergy lists.
     * Two requests that finish at the same time keep the first one stored.
     */
    String save(String recipeId, String conditionsHash, Map<String, Object> conditions, AdaptationResponse response,
                String aiModel) {
        return jdbc.sql("""
                        INSERT INTO adapted_recipe (recipe_id, conditions_hash, conditions_json, status, payload, ai_model)
                        VALUES (:recipeId, :hash, CAST(:conditions AS jsonb), :status, CAST(:payload AS jsonb), :aiModel)
                        ON CONFLICT (recipe_id, conditions_hash) DO UPDATE SET updated_by = 'SYSTEM'
                        RETURNING id
                        """)
                .param("recipeId", recipeId)
                .param("hash", conditionsHash)
                .param("conditions", Json.write(conditions))
                .param("status", response.status().name())
                .param("payload", Json.write(response))
                .param("aiModel", aiModel)
                .query(String.class).single();
    }

    private static Entry entry(String id, String payload, String conditions) {
        Map<String, String> codes = Json.parse(conditions, StoredConditions.class).ingredientCodes();
        return new Entry(id.strip(), Json.parse(payload, AdaptationResponse.class).withId(id.strip()),
                codes == null ? Map.of() : codes);
    }
}
