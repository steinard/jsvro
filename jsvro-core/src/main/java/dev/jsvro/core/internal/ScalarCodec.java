package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class ScalarCodec implements ValueCodec {
    private final JsvroType type;

    ScalarCodec(JsvroType type) {
        this.type = type;
    }

    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.scalar(name, type);
    }

    @Override
    public void write(JsonGenerator generator, Object value) {
        if (value == null) {
            generator.writeNull();
        }
        else {
            generator.writePOJO(value);
        }
    }

    @Override
    public JsonNode expand(JsonNode positionalValue, ObjectMapper mapper) {
        return positionalValue;
    }
}
