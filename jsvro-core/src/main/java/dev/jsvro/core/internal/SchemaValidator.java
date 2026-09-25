package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import dev.jsvro.core.JsvroSchema;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.util.List;

public final class SchemaValidator {
    private SchemaValidator() {
    }

    public static void validate(JsvroSchema expected, JsonParser parser) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new JsvroException("JSVRO stream must start with a schema object");
        }
        boolean versionSeen = false;
        boolean columnsSeen = false;
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "jsvro" -> {
                    if (value != JsonToken.VALUE_STRING || !expected.jsvro().equals(parser.getString())) {
                        throw unsupportedVersion(expected, describe(parser, value));
                    }
                    versionSeen = true;
                }
                case "columns" -> {
                    if (value != JsonToken.START_ARRAY) {
                        throw new JsvroException("JSVRO schema must contain a columns array");
                    }
                    validateColumns(expected.columns(), parser, "columns");
                    columnsSeen = true;
                }
                default -> parser.skipChildren();
            }
        }
        if (!versionSeen) {
            throw unsupportedVersion(expected, "nothing");
        }
        if (!columnsSeen) {
            throw new JsvroException("JSVRO schema must contain a columns array");
        }
    }

    private static void validateColumns(List<JsvroColumn> expected, JsonParser parser, String path) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw mismatch(path, "a columns array", describe(parser, parser.currentToken()));
        }
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (index == expected.size()) {
                int actual = index + 1;
                parser.skipChildren();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    parser.skipChildren();
                    actual++;
                }
                throw columnCount(path, expected.size(), actual);
            }
            validateColumn(expected.get(index), parser, path + "[" + index + "]", true);
            index++;
        }
        if (index != expected.size()) {
            throw columnCount(path, expected.size(), index);
        }
    }

    private static void validateColumn(JsvroColumn expected, JsonParser parser, String path, boolean named) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw mismatch(path, "a column object", describe(parser, parser.currentToken()));
        }
        boolean nameSeen = false;
        boolean typeSeen = false;
        boolean columnsSeen = false;
        boolean itemsSeen = false;
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "name" -> {
                    if (named) {
                        expectString(expected.name(), parser, value, path + ".name");
                        nameSeen = true;
                    }
                    else {
                        parser.skipChildren();
                    }
                }
                case "type" -> {
                    expectString(expected.type().wireName(), parser, value, path + ".type");
                    typeSeen = true;
                }
                case "columns" -> {
                    if (expected.columns().isEmpty()) {
                        parser.skipChildren();
                    }
                    else {
                        validateColumns(expected.columns(), parser, path + ".columns");
                        columnsSeen = true;
                    }
                }
                case "items" -> {
                    if (expected.items() == null) {
                        parser.skipChildren();
                    }
                    else {
                        validateColumn(expected.items(), parser, path + ".items", false);
                        itemsSeen = true;
                    }
                }
                default -> parser.skipChildren();
            }
        }
        if (named && !nameSeen) {
            throw mismatch(path + ".name", quoted(expected.name()), "nothing");
        }
        if (!typeSeen) {
            throw mismatch(path + ".type", quoted(expected.type().wireName()), "nothing");
        }
        if (!expected.columns().isEmpty() && !columnsSeen) {
            throw mismatch(path + ".columns", "a columns array", "nothing");
        }
        if (expected.items() != null && !itemsSeen) {
            throw mismatch(path + ".items", "a column object", "nothing");
        }
    }

    private static void expectString(String expected, JsonParser parser, JsonToken value, String path) {
        if (value != JsonToken.VALUE_STRING || !expected.equals(parser.getString())) {
            throw mismatch(path, quoted(expected), describe(parser, value));
        }
    }

    private static String describe(JsonParser parser, JsonToken token) {
        if (token == null) {
            return "nothing";
        }
        return switch (token) {
            case VALUE_STRING -> quoted(parser.getString());
            case START_OBJECT -> "an object";
            case START_ARRAY -> "an array";
            default -> parser.getString();
        };
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static JsvroException unsupportedVersion(JsvroSchema expected, String actual) {
        return new JsvroException("Unsupported JSVRO version: expected " + quoted(expected.jsvro()) + " but got " + actual);
    }

    private static JsvroException columnCount(String path, int expected, int actual) {
        return new JsvroException("Schema mismatch at " + path + ": expected " + expected + " columns but got " + actual);
    }

    private static JsvroException mismatch(String path, String expected, String actual) {
        return new JsvroException("Schema mismatch at " + path + ": expected " + expected + " but got " + actual);
    }
}
