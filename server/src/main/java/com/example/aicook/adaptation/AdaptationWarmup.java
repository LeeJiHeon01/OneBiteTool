package com.example.aicook.adaptation;

import com.example.aicook.adaptation.AdaptationRequest.Profile;
import com.example.aicook.recipe.Recipe.SkillLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Makes the adaptations of the fixture recipes ahead of time for a few typical settings, so a
 * demonstration opens them at once. Off unless app.adaptation-warmup-enabled is set; each
 * setting that is not stored yet costs one AI call. No allergies are involved.
 */
@Component
class AdaptationWarmup {

    private static final Logger log = LoggerFactory.getLogger(AdaptationWarmup.class);
    /** A kitchen without measuring tools: spoons, a paper cup, a knife, a stove, a pan and a pot. */
    private static final List<String> TYPICAL_TOOLS = List.of("spoon_rice", "teaspoon", "paper_cup_small", "knife",
            "gas_stove", "frying_pan", "pot");

    private final AdaptationService adaptations;
    private final JdbcClient jdbc;
    private final boolean enabled;

    AdaptationWarmup(AdaptationService adaptations, JdbcClient jdbc,
                     @Value("${app.adaptation-warmup-enabled}") boolean enabled) {
        this.adaptations = adaptations;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmUp() {
        if (!enabled) {
            return;
        }
        // In the background: the server answers requests while this runs.
        Thread.ofVirtual().name("adaptation-warmup").start(this::run);
    }

    void run() {
        record Fixture(String id, double servings) {
        }
        List<Fixture> fixtures = jdbc.sql("SELECT id, base_servings FROM recipe WHERE source = 'FIXTURE' ORDER BY id")
                .query((rs, row) -> new Fixture(rs.getString("id").strip(), rs.getDouble("base_servings")))
                .list();
        for (Fixture fixture : fixtures) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                try {
                    Profile profile = new Profile(skillLevel, TYPICAL_TOOLS, null, List.of());
                    adaptations.adapt(new AdaptationRequest(fixture.id(), (int) fixture.servings(), List.of(),
                            List.of(), profile));
                } catch (RuntimeException exception) {
                    log.warn("Warm-up of recipe {} for {} failed: {}", fixture.id(), skillLevel,
                            exception.getClass().getSimpleName());
                }
            }
        }
        log.info("Adaptation warm-up finished for {} fixture recipes", fixtures.size());
    }
}
