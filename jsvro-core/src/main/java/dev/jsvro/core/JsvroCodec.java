package dev.jsvro.core;

import dev.jsvro.core.internal.CodecFactory;
import dev.jsvro.core.internal.NonClosingStreams;
import dev.jsvro.core.internal.SchemaValidator;
import dev.jsvro.core.internal.SchemaWriter;
import dev.jsvro.core.internal.ValueCodec;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * JSVRO encoder/decoder facade.
 *
 * <p>The encoder is streaming: the schema is written once followed by one positional JSON value per row.
 * The decoder materializes one row tree at a time and delegates final Java binding to Jackson.</p>
 *
 * <p>Caller-provided input and output streams remain caller-owned and are not closed by this class.</p>
 */
public final class JsvroCodec {
    private static final SerializableString NO_ROOT_SEPARATOR = new SerializedString("");

    private final ObjectMapper mapper;
    private final CodecFactory codecs;

    public JsvroCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.codecs = new CodecFactory(mapper);
    }

    public JsvroSchema schema(Class<?> elementType) {
        return schema(mapper.constructType(elementType));
    }

    public JsvroSchema schema(JavaType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        ValueCodec codec = codecs.codec(elementType);
        JsvroColumn root = codec.column("root");
        if (root.type() != JsvroType.OBJECT) {
            throw new JsvroException("JSVRO root row type must be an object, got " + root.type().wireName());
        }
        return new JsvroSchema(root.columns());
    }

    public void write(OutputStream output, Class<?> elementType, Iterable<?> rows) {
        Objects.requireNonNull(rows, "rows");
        write(output, mapper.constructType(elementType), rows.iterator());
    }

    public void write(OutputStream output, JavaType elementType, Iterable<?> rows) {
        Objects.requireNonNull(rows, "rows");
        write(output, elementType, rows.iterator());
    }

    public void write(OutputStream output, JavaType elementType, Stream<?> rows) {
        Objects.requireNonNull(rows, "rows");
        try (rows) {
            write(output, elementType, rows.iterator());
        }
    }

    public <T> List<T> readList(InputStream input, Class<T> elementType) {
        JavaType javaType = mapper.constructType(elementType);
        List<?> values = readList(input, javaType);
        @SuppressWarnings("unchecked")
        List<T> typed = (List<T>) values;
        return typed;
    }

    public List<?> readList(InputStream input, JavaType elementType) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(elementType, "elementType");

        ValueCodec codec = codecs.codec(elementType);
        JsvroSchema expectedSchema = schema(elementType);
        List<Object> result = new ArrayList<>();
        // A JSVRO stream intentionally contains multiple root JSON values.
        var reader = mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        try (JsonParser parser = mapper.createParser(NonClosingStreams.input(input))) {
            if (parser.nextToken() == null) {
                throw new JsvroException("Empty JSVRO stream");
            }

            JsonNode header = reader.readTree(parser);
            SchemaValidator.validate(expectedSchema, header);

            while (parser.nextToken() != null) {
                JsonNode positional = reader.readTree(parser);
                JsonNode expanded = codec.expand(positional, mapper);
                result.add(mapper.treeToValue(expanded, elementType));
            }
            return result;
        }
    }

    public int cachedCodecCount() {
        return codecs.cachedCodecCount();
    }

    private void write(OutputStream output, JavaType elementType, Iterator<?> rows) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(elementType, "elementType");
        Objects.requireNonNull(rows, "rows");

        ValueCodec codec = codecs.codec(elementType);
        JsvroSchema schema = schema(elementType);

        // We write newlines ourselves; prevent Jackson from adding its default root-level space separator.
        try (JsonGenerator generator = mapper.writer().withRootValueSeparator(NO_ROOT_SEPARATOR)
                .createGenerator(NonClosingStreams.output(output))) {

            SchemaWriter.write(generator, schema);
            generator.writeRaw('\n');

            while (rows.hasNext()) {
                codec.write(generator, rows.next());
                generator.writeRaw('\n');
            }
            generator.flush();
        }
    }
}
