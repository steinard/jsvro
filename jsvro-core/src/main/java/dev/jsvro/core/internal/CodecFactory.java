package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroException;
import dev.jsvro.core.JsvroType;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CodecFactory {
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<JavaType, ValueCodec> cache = new ConcurrentHashMap<>();

    public CodecFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ValueCodec codec(JavaType type) {
        ValueCodec cached = cache.get(type);
        if (cached != null) {
            return cached;
        }
        return build(type, new HashSet<>());
    }

    public int cachedCodecCount() {
        return cache.size();
    }

    private ValueCodec build(JavaType type, Set<JavaType> path) {
        ValueCodec cached = cache.get(type);
        if (cached != null) {
            return cached;
        }

        ValueCodec scalar = scalarCodec(type);
        if (scalar != null) {
            return cache(type, scalar);
        }

        Class<?> raw = type.getRawClass();

        if (Map.class.isAssignableFrom(raw)) {
            return cache(type, new MapCodec());
        }

        if (type.isArrayType()) {
            return cache(type, new ArrayCodec(build(type.getContentType(), path)));
        }

        if (Iterable.class.isAssignableFrom(raw)) {
            JavaType itemType = type.getContentType();
            if (itemType == null) {
                throw new JsvroException("Collection element type is required for " + type);
            }
            return cache(type, new IterableCodec(build(itemType, path)));
        }

        if (raw.isInterface() || raw.isAnnotation()) {
            throw new JsvroException("Cannot derive a positional schema from abstract type " + type);
        }

        if (!path.add(type)) {
            throw new JsvroException("Recursive object graph is not supported in JSVRO v1: " + type);
        }

        try {
            var introspector = mapper.serializationConfig().classIntrospectorInstance();
            BeanDescription bean = introspector.introspectForSerialization(
                    type, introspector.introspectClassAnnotations(type));
            var bindings = new ArrayList<PropertyBinding>();

            for (BeanPropertyDefinition property : bean.findProperties()) {
                if (!property.couldSerialize()) {
                    continue;
                }
                AnnotatedMember accessor = property.getAccessor();
                if (accessor == null) {
                    continue;
                }
                if (mapper.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) {
                    accessor.fixAccess(mapper.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS));
                }

                JavaType propertyType = property.getPrimaryType();
                ValueCodec propertyCodec = build(propertyType, path);
                bindings.add(new PropertyBinding(
                        property.getName(),
                        PropertyAccessor.from(accessor),
                        propertyCodec));
            }

            if (bindings.isEmpty()) {
                throw new JsvroException("No serializable properties found for " + type);
            }

            return cache(type, new ObjectCodec(bindings));
        }
        finally {
            path.remove(type);
        }
    }

    private ValueCodec cache(JavaType type, ValueCodec codec) {
        ValueCodec existing = cache.putIfAbsent(type, codec);
        return existing != null ? existing : codec;
    }

    private ValueCodec scalarCodec(JavaType type) {
        Class<?> raw = type.getRawClass();

        if (raw == String.class || raw == char.class || raw == Character.class || raw.isEnum()) {
            return new ScalarCodec(JsvroType.STRING);
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return new ScalarCodec(JsvroType.BOOLEAN);
        }
        if (raw == byte.class || raw == Byte.class
                || raw == short.class || raw == Short.class
                || raw == int.class || raw == Integer.class
                || raw == long.class || raw == Long.class
                || raw == BigInteger.class) {
            return new ScalarCodec(JsvroType.INTEGER);
        }
        if (raw == float.class || raw == Float.class
                || raw == double.class || raw == Double.class) {
            return new ScalarCodec(JsvroType.NUMBER);
        }
        if (raw == BigDecimal.class) {
            return new ScalarCodec(JsvroType.DECIMAL);
        }
        if (raw == LocalDate.class) {
            return new ScalarCodec(JsvroType.DATE);
        }
        if (raw == Instant.class || raw == LocalDateTime.class
                || raw == OffsetDateTime.class || raw == ZonedDateTime.class) {
            return new ScalarCodec(JsvroType.DATETIME);
        }
        if (raw == UUID.class) {
            return new ScalarCodec(JsvroType.UUID);
        }
        if (raw == byte[].class) {
            return new ScalarCodec(JsvroType.BINARY);
        }
        return null;
    }
}
