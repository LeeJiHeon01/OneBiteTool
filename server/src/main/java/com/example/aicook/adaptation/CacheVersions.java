package com.example.aicook.adaptation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * What a stored adaptation was made with. A stored answer is only used again while the
 * reference data, the rewording prompt and the model are the same as when it was made.
 */
@Component
class CacheVersions {

    /** Raised by hand when the adaptation logic changes in a way that makes stored answers wrong. */
    static final int LOGIC_VERSION = 1;

    private final String seedVersion;
    private final String promptVersion;
    private final String model;

    CacheVersions(@Value("${openai.model}") String model) {
        this.model = model;
        this.seedVersion = hashOf("classpath*:seed/*.json");
        // The prompt, its answer schema and the model that reads them decide the wording.
        this.promptVersion = hashOf("classpath*:prompts/recipe-adapt.md", "classpath*:prompts/schemas/recipe-adapt.json")
                + ":" + model;
    }

    /** Changes whenever a file under data/seed changes. */
    String seedVersion() {
        return seedVersion;
    }

    /** Changes whenever the rewording prompt, its schema or the model changes. */
    String promptVersion() {
        return promptVersion;
    }

    String model() {
        return model;
    }

    /** Short hash over the named files, read in a fixed order. */
    private static String hashOf(String... locations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (String location : locations) {
                Resource[] resources = resolver.getResources(location);
                Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
                for (Resource resource : resources) {
                    digest.update(resource.getFilename().getBytes(StandardCharsets.UTF_8));
                    try (InputStream stream = resource.getInputStream()) {
                        digest.update(stream.readAllBytes());
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
