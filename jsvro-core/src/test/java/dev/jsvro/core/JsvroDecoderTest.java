package dev.jsvro.core;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroDecoderTest {
    private static final String HEADER =
            "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"integer\"}]}\n";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroCodec codec = new JsvroCodec(mapper);

    record Person(String name, int age) {}

    @Test
    void rejectsNullRows() {
        assertDecodeFails(HEADER + "[\"Alice\",30]\nnull\n", "Expected row 1 to be a JSON array but got VALUE_NULL");
    }

    @Test
    void rejectsRowsWithTooFewValues() {
        assertDecodeFails(HEADER + "[\"Alice\"]\n", "Expected 2 positional values at row 0 but got 1");
    }

    @Test
    void rejectsRowsWithTooManyValues() {
        assertDecodeFails(HEADER + "[\"Alice\",30,true]\n", "Expected 2 positional values at row 0 but got more");
    }

    @Test
    void rejectsUnsupportedVersions() {
        assertDecodeFails("{\"jsvro\":\"2\",\"columns\":[]}\n", "Unsupported JSVRO version: expected \"1\" but got \"2\"");
    }

    @Test
    void rejectsNonStringVersions() {
        assertDecodeFails("{\"jsvro\":1,\"columns\":[]}\n", "Unsupported JSVRO version: expected \"1\" but got 1");
    }

    @Test
    void rejectsEmptyStreams() {
        assertDecodeFails("", "Empty JSVRO stream");
    }

    @Test
    void schemaMismatchesReportExpectedAndActualValues() {
        String header = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"string\"}]}\n";

        assertDecodeFails(header, "Schema mismatch at columns[1].type: expected \"integer\" but got \"string\"");
    }

    @Test
    void readStreamDecodesLazily() {
        String input = HEADER + "[\"Alice\",30]\n[\"broken\"]\n";

        try (Stream<Person> rows = codec.readStream(stream(input), Person.class)) {
            Iterator<Person> iterator = rows.iterator();
            assertEquals(new Person("Alice", 30), iterator.next());
            assertThrows(JsvroException.class, iterator::next);
        }
    }

    @Test
    void closingTheReadStreamLeavesTheCallersInputOpen() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream input = new ByteArrayInputStream((HEADER + "[\"Alice\",30]\n").getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };

        try (Stream<Person> rows = codec.readStream(input, Person.class)) {
            assertEquals(List.of(new Person("Alice", 30)), rows.toList());
        }
        assertFalse(closed.get());
    }

    @Test
    void writingAStreamClosesIt() {
        AtomicBoolean closed = new AtomicBoolean();
        Stream<Person> rows = Stream.of(new Person("Alice", 30)).onClose(() -> closed.set(true));

        codec.write(new ByteArrayOutputStream(), mapper.constructType(Person.class), rows);

        assertTrue(closed.get());
    }

    private void assertDecodeFails(String input, String expectedMessage) {
        var failure = assertThrows(JsvroException.class, () -> codec.readList(stream(input), Person.class));
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static InputStream stream(String input) {
        return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    }
}
