package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import dev.jsvro.core.JsvroSchema;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JacksonSerializable;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.util.TokenBuffer;

import java.util.Iterator;
import java.util.List;

public final class RootCodec {
    private final JsvroSchema schema;
    private final ObjectCodec codec;

    RootCodec(JsvroSchema schema, ObjectCodec codec) {
        this.schema = schema;
        this.codec = codec;
    }

    public JsvroSchema schema() {
        return schema;
    }

    public void write(JsonGenerator generator, Iterator<?> rows) {
        generator.objectWriteContext().writeValue(generator, new JacksonSerializable.Base() {
            @Override
            public void serialize(JsonGenerator gen, SerializationContext context) {
                SchemaWriter.write(gen, schema);
                gen.writeRaw('\n');
                long index = 0;
                while (rows.hasNext()) {
                    Object row = rows.next();
                    if (row == null) {
                        throw new JsvroException("Row " + index + " is null; JSVRO rows must be objects");
                    }
                    codec.write(row, gen, context);
                    gen.writeRaw('\n');
                    index++;
                }
            }

            @Override
            public void serializeWithType(JsonGenerator gen, SerializationContext context, TypeSerializer typeSerializer) {
                serialize(gen, context);
            }
        });
    }

    public Object readRow(JsonParser parser, long index, ObjectReader reader) {
        String path = "row " + index;
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new JsvroException("Expected " + path + " to be a JSON array but got " + parser.currentToken());
        }
        TokenBuffer named = TokenBuffer.forBuffering(parser, parser.objectReadContext());
        object(parser, schema.columns(), named, path);
        try (JsonParser namedParser = named.asParser(parser.objectReadContext())) {
            return reader.readValue(namedParser);
        }
    }

    static SerializationContext context(JsonGenerator generator) {
        if (generator.objectWriteContext() instanceof SerializationContext context) {
            return context;
        }
        throw new JsvroException("JSVRO requires a generator created by a Jackson ObjectMapper or ObjectWriter");
    }

    private static void value(JsonParser parser, JsvroColumn column, TokenBuffer out, String path) {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            out.writeNull();
            return;
        }
        switch (column.type()) {
            case OBJECT -> object(parser, column.columns(), out, path);
            case ARRAY -> array(parser, column.items(), out, path);
            default -> out.copyCurrentStructure(parser);
        }
    }

    private static void object(JsonParser parser, List<JsvroColumn> columns, TokenBuffer out, String path) {
        expectArray(parser, path);
        out.writeStartObject();
        for (int i = 0; i < columns.size(); i++) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_ARRAY || token == null) {
                throw new JsvroException("Expected " + columns.size() + " positional values at " + path + " but got " + i);
            }
            JsvroColumn column = columns.get(i);
            out.writeName(column.name());
            value(parser, column, out, path + "." + column.name());
        }
        if (parser.nextToken() != JsonToken.END_ARRAY) {
            throw new JsvroException("Expected " + columns.size() + " positional values at " + path + " but got more");
        }
        out.writeEndObject();
    }

    private static void array(JsonParser parser, JsvroColumn items, TokenBuffer out, String path) {
        expectArray(parser, path);
        out.writeStartArray();
        int index = 0;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            if (token == null) {
                throw new JsvroException("Unterminated array at " + path);
            }
            value(parser, items, out, path + "[" + index++ + "]");
        }
        out.writeEndArray();
    }

    private static void expectArray(JsonParser parser, String path) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new JsvroException("Expected a positional array at " + path + " but got " + parser.currentToken());
        }
    }
}
