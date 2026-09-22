package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

final class IterableCodec implements ValueCodec {
    private final ValueCodec itemCodec;

    IterableCodec(ValueCodec itemCodec) {
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
        for (Object item : (Iterable<?>) value) {
            itemCodec.write(generator, item);
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
