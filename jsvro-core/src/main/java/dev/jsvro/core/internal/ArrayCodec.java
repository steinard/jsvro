package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.lang.reflect.Array;

final class ArrayCodec implements ValueCodec {
    private final ValueCodec itemCodec;

    ArrayCodec(ValueCodec itemCodec) {
        this.itemCodec = itemCodec;
    }

    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.array(name, itemCodec.column("item"));
    }

    @Override
    public void write(JsonGenerator generator, Object value) {
        if (value == null) {
            generator.writeNull();
            return;
        }

        generator.writeStartArray();
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            itemCodec.write(generator, Array.get(value, i));
        }
        generator.writeEndArray();
    }

    @Override
    public JsonNode expand(JsonNode positionalValue, ObjectMapper mapper) {
        if (positionalValue == null || positionalValue.isNull()) {
            return mapper.nullNode();
        }
        if (!positionalValue.isArray()) {
            throw new JsvroException("Expected positional array value but got " + positionalValue.getNodeType());
        }

        ArrayNode result = mapper.createArrayNode();
        for (JsonNode value : positionalValue) {
            result.add(itemCodec.expand(value, mapper));
        }
        return result;
    }
}
