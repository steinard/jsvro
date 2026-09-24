package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

import java.util.List;

final class ObjectCodec implements ValueCodec {
    private final Class<?> type;
    private final List<Property> properties;

    ObjectCodec(Class<?> type, List<Property> properties) {
        this.type = type;
        this.properties = List.copyOf(properties);
    }

    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.object(name, properties.stream().map(Property::column).toList());
    }

    @Override
    public void write(Object value, JsonGenerator generator, SerializationContext context) {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (value.getClass() != type) {
            throw new JsvroException("JSVRO schema was derived from " + type.getName()
                    + " but the value is a " + value.getClass().getName()
                    + "; subtype properties cannot be represented positionally");
        }

        generator.writeStartArray(value, properties.size());
        for (Property property : properties) {
            property.write(value, generator, context);
        }
        generator.writeEndArray();
    }

    record Property(BeanPropertyWriter writer, ValueCodec codec) {
        JsvroColumn column() {
            return codec.column(writer.getName());
        }

        void write(Object bean, JsonGenerator generator, SerializationContext context) {
            try {
                if (codec instanceof LeafCodec) {
                    writer.serializeAsElement(bean, generator, context);
                }
                else {
                    codec.write(writer.get(bean), generator, context);
                }
            }
            catch (JacksonException | JsvroException ex) {
                throw ex;
            }
            catch (Exception ex) {
                throw new JsvroException("Could not write property '" + writer.getName() + "'", ex);
            }
        }
    }
}
