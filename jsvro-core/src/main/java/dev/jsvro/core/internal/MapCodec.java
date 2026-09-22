package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Maps retain ordinary JSON object representation because their keys are data, not a static schema. */
final class MapCodec implements ValueCodec {
    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.scalar(name, JsvroType.MAP);
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
