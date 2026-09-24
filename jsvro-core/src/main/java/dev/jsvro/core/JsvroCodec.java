package dev.jsvro.core;

import dev.jsvro.core.internal.CodecFactory;
import dev.jsvro.core.internal.NonClosingStreams;
import dev.jsvro.core.internal.RootCodec;
import dev.jsvro.core.internal.SchemaValidator;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JSVRO encoder/decoder facade.
 *
 * <p>The encoder is streaming: the schema is written once followed by one positional JSON value per row.
 * The decoder buffers one row at a time and delegates final Java binding to Jackson.</p>
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
        return codecs.root(elementType).schema();
    }

    public boolean supports(JavaType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return codecs.supports(elementType);
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
        @SuppressWarnings("unchecked")
        List<T> typed = (List<T>) readList(input, mapper.constructType(elementType));
        return typed;
    }

    public List<?> readList(InputStream input, JavaType elementType) {
        try (Stream<?> rows = readStream(input, elementType)) {
            return rows.collect(Collectors.toCollection(ArrayList::new));
        }
    }

    public <T> Stream<T> readStream(InputStream input, Class<T> elementType) {
        @SuppressWarnings("unchecked")
        Stream<T> typed = (Stream<T>) readStream(input, mapper.constructType(elementType));
        return typed;
    }

    public Stream<?> readStream(InputStream input, JavaType elementType) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(elementType, "elementType");

        RootCodec root = codecs.root(elementType);
        ObjectReader reader = mapper.readerFor(elementType);
        JsonParser parser = mapper.createParser(NonClosingStreams.input(input));
        try {
            if (parser.nextToken() == null) {
                throw new JsvroException("Empty JSVRO stream");
            }
            // A JSVRO stream intentionally contains multiple root JSON values.
            JsonNode header = mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(parser);
            SchemaValidator.validate(root.schema(), header);

            Iterator<Object> rows = new RowIterator(parser, root, reader);
            return StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED | Spliterator.NONNULL), false)
                    .onClose(parser::close);
        }
        catch (RuntimeException ex) {
            parser.close();
            throw ex;
        }
    }

    public int cachedCodecCount() {
        return codecs.cachedCodecCount();
    }

    private void write(OutputStream output, JavaType elementType, Iterator<?> rows) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(elementType, "elementType");
        Objects.requireNonNull(rows, "rows");

        RootCodec root = codecs.root(elementType);

        // We write newlines ourselves; prevent Jackson from adding its default root-level space separator.
        try (JsonGenerator generator = mapper.writer().withRootValueSeparator(NO_ROOT_SEPARATOR)
                .createGenerator(NonClosingStreams.output(output))) {

            root.write(generator, rows);
            generator.flush();
        }
    }

    private static final class RowIterator implements Iterator<Object> {
        private final JsonParser parser;
        private final RootCodec root;
        private final ObjectReader reader;
        private long index;
        private Object next;

        private RowIterator(JsonParser parser, RootCodec root, ObjectReader reader) {
            this.parser = parser;
            this.root = root;
            this.reader = reader;
        }

        @Override
        public boolean hasNext() {
            if (next == null && parser.nextToken() != null) {
                next = root.readRow(parser, index++, reader);
            }
            return next != null;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Object row = next;
            next = null;
            return row;
        }
    }
}
