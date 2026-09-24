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
        JsonNode version = actual.path("jsvro");
        if (!version.isString() || !expected.jsvro().equals(version.stringValue())) {
            throw new JsvroException("Unsupported JSVRO version: expected \"" + expected.jsvro() + "\" but got " + describe(version));
        }

        JsonNode columns = actual.path("columns");
        if (!columns.isArray()) {
            throw new JsvroException("JSVRO schema must contain a columns array");
        }
        validateColumns(expected.columns(), columns, "columns");
    }

    private static void validateColumns(List<JsvroColumn> expected, JsonNode actual, String path) {
        if (!actual.isArray()) {
            throw mismatch(path, "a columns array", actual);
        }
        if (actual.size() != expected.size()) {
            throw new JsvroException(
                    "Schema mismatch at " + path + ": expected " + expected.size() + " columns but got " + actual.size());
        }

        for (int i = 0; i < expected.size(); i++) {
            JsvroColumn expectedColumn = expected.get(i);
            JsonNode actualColumn = actual.get(i);
            String columnPath = path + "[" + i + "]";

            JsonNode name = actualColumn.path("name");
            if (!name.isString() || !expectedColumn.name().equals(name.stringValue())) {
                throw mismatch(columnPath + ".name", "\"" + expectedColumn.name() + "\"", name);
            }
            validateShape(expectedColumn, actualColumn, columnPath);
        }
    }

    private static void validateShape(JsvroColumn expected, JsonNode actual, String path) {
        if (!actual.isObject()) {
            throw mismatch(path, "a column object", actual);
        }
        JsonNode type = actual.path("type");
        if (!type.isString() || !expected.type().wireName().equals(type.stringValue())) {
            throw mismatch(path + ".type", "\"" + expected.type().wireName() + "\"", type);
        }
        if (!expected.columns().isEmpty()) {
            validateColumns(expected.columns(), actual.path("columns"), path + ".columns");
        }
        if (expected.items() != null) {
            validateShape(expected.items(), actual.path("items"), path + ".items");
        }
    }

    private static JsvroException mismatch(String path, String expected, JsonNode actual) {
        return new JsvroException("Schema mismatch at " + path + ": expected " + expected + " but got " + describe(actual));
    }

    private static String describe(JsonNode node) {
        return node.isMissingNode() ? "nothing" : node.toString();
    }
}
