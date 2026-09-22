package dev.jsvro.spring;

import dev.jsvro.core.JsvroCodec;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

/** Spring MVC converter for JSVRO. Controllers can keep returning ordinary List/Collection/Stream values. */
public final class JsvroHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
    private final JsonMapper mapper;
    private final JsvroCodec codec;

    public JsvroHttpMessageConverter(JsonMapper mapper, JsvroCodec codec) {
        super(JsvroMediaType.APPLICATION_JSVRO);
        this.mapper = mapper;
        this.codec = codec;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return clazz.isArray()
                || Iterable.class.isAssignableFrom(clazz)
                || Stream.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return isJsvro(mediaType) && resolveElementType(type, clazz) != null;
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return isJsvro(mediaType) && isListType(type) && resolveElementType(type, contextClass) != null;
    }

    @Override
    protected void writeInternal(Object value, Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {

        JavaType elementType = resolveElementType(type, value.getClass());
        if (elementType == null) {
            throw new HttpMessageNotWritableException("Cannot determine JSVRO element type from " + type);
        }

        if (value instanceof Stream<?> stream) {
            codec.write(outputMessage.getBody(), elementType, stream);
        }
        else if (value instanceof Iterable<?> iterable) {
            codec.write(outputMessage.getBody(), elementType, iterable);
        }
        else if (value.getClass().isArray()) {
            codec.write(outputMessage.getBody(), elementType, arrayIterable(value));
        }
        else {
            throw new HttpMessageNotWritableException("Unsupported JSVRO response type " + value.getClass().getName());
        }
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {

        JavaType elementType = resolveElementType(type, contextClass);
        if (elementType == null) {
            throw new HttpMessageNotReadableException("Cannot determine JSVRO element type from " + type, inputMessage);
        }
        return codec.readList(inputMessage.getBody(), elementType);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException(
                "JSVRO requires a generic target such as List<Person>", inputMessage);
    }

    private boolean isJsvro(MediaType mediaType) {
        // Do not claim */* or application/*: ordinary JSON should remain the default
        // unless the caller explicitly asks for the JSVRO representation.
        return mediaType != null
                && !mediaType.isWildcardType()
                && !mediaType.isWildcardSubtype()
                && JsvroMediaType.APPLICATION_JSVRO.isCompatibleWith(mediaType);
    }

    private JavaType resolveElementType(Type type, Class<?> fallbackClass) {
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> rawClass
                    && (Iterable.class.isAssignableFrom(rawClass) || Stream.class.isAssignableFrom(rawClass))) {
                Type element = parameterized.getActualTypeArguments()[0];
                return mapper.getTypeFactory().constructType(element);
            }
        }
        if (type instanceof GenericArrayType genericArray) {
            return mapper.getTypeFactory().constructType(genericArray.getGenericComponentType());
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return mapper.constructType(clazz.getComponentType());
        }
        if (fallbackClass != null && fallbackClass.isArray()) {
            return mapper.constructType(fallbackClass.getComponentType());
        }
        return null;
    }

    private boolean isListType(Type type) {
        if (!(type instanceof ParameterizedType parameterized)) {
            return false;
        }
        Type raw = parameterized.getRawType();
        return raw instanceof Class<?> rawClass && List.class.isAssignableFrom(rawClass);
    }

    private Iterable<Object> arrayIterable(Object array) {
        return () -> new java.util.Iterator<>() {
            private final int length = java.lang.reflect.Array.getLength(array);
            private int index;

            @Override
            public boolean hasNext() {
                return index < length;
            }

            @Override
            public Object next() {
                return java.lang.reflect.Array.get(array, index++);
            }
        };
    }
}
