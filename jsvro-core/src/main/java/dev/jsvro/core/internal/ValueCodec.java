package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public interface ValueCodec {
    JsvroColumn column(String name);

    void write(JsonGenerator generator, Object value);

    JsonNode expand(JsonNode positionalValue, ObjectMapper mapper);
}
