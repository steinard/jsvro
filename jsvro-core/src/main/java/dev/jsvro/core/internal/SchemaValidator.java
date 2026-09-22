package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import dev.jsvro.core.JsvroSchema;
import tools.jackson.databind.JsonNode;

import java.util.List;

public final class SchemaValidator {
    private SchemaValidator() {
    }

    public static void validate(JsvroSchema expected, JsonNode actual) {
        if (actual == null || !actual.isObject()) {
            throw new JsvroException("JSVRO stream must start with a schema object");
        }
        if (!expected.jsvro().equals(actual.path("jsvro").asText())) {
            throw new JsvroException("Unsupported JSVRO version: " + actual.path("jsvro").asText());
        }

        JsonNode columns = actual.path("columns");
        if (!columns.isArray()) {
            throw new JsvroException("JSVRO schema must contain a columns array");
        }
        validateColumns(expected.columns(), columns, "columns");
    }

    private static void validateColumns(List<JsvroColumn> expected, JsonNode actual, String path) {
        if (actual.size() != expected.size()) {
            throw new JsvroException(
                    "Schema mismatch at " + path + ": expected " + expected.size() + " columns but got " + actual.size());
        }

        for (int i = 0; i < expected.size(); i++) {
            JsvroColumn expectedColumn = expected.get(i);
            JsonNode actualColumn = actual.get(i);
            String columnPath = path + "[" + i + "]";

            if (!expectedColumn.name().equals(actualColumn.path("name").asText())) {
                throw new JsvroException("Schema mismatch at " + columnPath + ".name");
            }
            if (!expectedColumn.type().wireName().equals(actualColumn.path("type").asText())) {
                throw new JsvroException("Schema mismatch at " + columnPath + ".type");
            }

            if (!expectedColumn.columns().isEmpty()) {
                validateColumns(expectedColumn.columns(), actualColumn.path("columns"), columnPath + ".columns");
            }

            if (expectedColumn.items() != null) {
                validateItem(expectedColumn.items(), actualColumn.path("items"), columnPath + ".items");
            }
        }
    }

    private static void validateItem(JsvroColumn expected, JsonNode actual, String path) {
        if (!actual.isObject()) {
            throw new JsvroException("Schema mismatch at " + path + ": expected item object");
        }
        if (!expected.type().wireName().equals(actual.path("type").asText())) {
            throw new JsvroException("Schema mismatch at " + path + ".type");
        }
        if (!expected.columns().isEmpty()) {
            validateColumns(expected.columns(), actual.path("columns"), path + ".columns");
        }
        if (expected.items() != null) {
            validateItem(expected.items(), actual.path("items"), path + ".items");
        }
    }
}
