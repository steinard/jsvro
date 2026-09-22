package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

final class ObjectCodec implements ValueCodec {
    private final List<PropertyBinding> properties;

    ObjectCodec(List<PropertyBinding> properties) {
        this.properties = List.copyOf(properties);
    }

    List<PropertyBinding> properties() {
        return properties;
    }

    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.object(
                name,
                properties.stream()
                        .map(property -> property.codec().column(property.name()))
                        .toList());
    }

    @Override
    public void write(JsonGenerator generator, Object value) {
        if (value == null) {
            generator.writeNull();
            return;
        }

        generator.writeStartArray();
        for (PropertyBinding property : properties) {
            Object propertyValue;
            try {
                propertyValue = property.accessor().get(value);
            }
            catch (RuntimeException ex) {
                throw new JsvroException("Could not read property '" + property.name() + "'", ex);
            }
            property.codec().write(generator, propertyValue);
        }
        generator.writeEndArray();
    }

    @Override
    public JsonNode expand(JsonNode positionalValue, ObjectMapper mapper) {
        if (positionalValue == null || positionalValue.isNull()) {
            return mapper.nullNode();
        }
        if (!positionalValue.isArray()) {
            throw new JsvroException("Expected positional object array but got " + positionalValue.getNodeType());
        }
        if (positionalValue.size() != properties.size()) {
            throw new JsvroException(
                    "Expected " + properties.size() + " positional values but got " + positionalValue.size());
        }

        ObjectNode result = mapper.createObjectNode();
        for (int i = 0; i < properties.size(); i++) {
            PropertyBinding property = properties.get(i);
            result.set(property.name(), property.codec().expand(positionalValue.get(i), mapper));
        }
        return result;
    }
}
