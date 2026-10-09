package com.example.aicook.common;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads classpath files (seed, fixtures, prompts) and converts JSON exchanged with the AI API. */
public final class Json {

    // Seed files carry notes for people (status, note, _todo) that the server ignores.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Json() {
    }

    public static <T> T readResource(String path, Class<T> type) {
        try (InputStream stream = Json.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("File not found on classpath: " + path);
            }
            return read(stream, type);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static <T> T read(InputStream stream, Class<T> type) {
        return MAPPER.readValue(stream, type);
    }

    public static String readText(String path) {
        try (InputStream stream = Json.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("File not found on classpath: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
