package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

interface ValueCodec {
    JsvroColumn column(String name);

    void write(Object value, JsonGenerator generator, SerializationContext context);
}
