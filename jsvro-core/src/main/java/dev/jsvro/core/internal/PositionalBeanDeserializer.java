package dev.jsvro.core.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanAsArrayDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.PropertyBasedCreator;
import tools.jackson.databind.deser.bean.PropertyValueBuffer;
import tools.jackson.databind.introspect.AnnotatedWithParams;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

final class PositionalBeanDeserializer extends BeanAsArrayDeserializer {
    private final Map<Class<?>, Construction> constructions;
    private final FastCreator fastCreator;
    private final SettableBeanProperty[] creatorProperties;
    private final int[] argumentIndexes;
    private volatile boolean fastEnabled;

    PositionalBeanDeserializer(BeanDeserializerBase named, List<String> columns,
            Map<Class<?>, Construction> constructions) {
        super(named, columns.stream()
                .map(column -> named.findProperty(PropertyName.construct(column)))
                .toArray(SettableBeanProperty[]::new));
        this.constructions = constructions;
        this.creatorProperties = new SettableBeanProperty[_orderedProperties.length];
        this.argumentIndexes = new int[_orderedProperties.length];
        this.fastCreator = fastCreator();
        this.fastEnabled = fastCreator != null;
        constructions.put(_beanType.getRawClass(), fastEnabled ? Construction.FAST : Construction.JACKSON);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) {
        if (_needViewProcesing && context.getActiveView() != null) {
            return super.deserialize(parser, context);
        }
        if (fastCreator != null) {
            return deserializeWithFastCreator(parser, context);
        }
        if (_propertyBasedCreator != null) {
            return deserializeWithCreator(parser, context);
        }
        if (_vanillaProcessing) {
            return deserializeWithSetters(parser, context);
        }
        return super.deserialize(parser, context);
    }

    private FastCreator fastCreator() {
        if (_propertyBasedCreator == null || _injectables != null || _objectIdReader != null) {
            return null;
        }
        AnnotatedWithParams creator = _valueInstantiator.getWithArgsCreator();
        if (creator == null) {
            return null;
        }
        BitSet covered = new BitSet();
        for (int i = 0; i < _orderedProperties.length; i++) {
            SettableBeanProperty property = _orderedProperties[i];
            if (property == null) {
                argumentIndexes[i] = -1;
                continue;
            }
            SettableBeanProperty creatorProperty = _propertyBasedCreator.findCreatorProperty(property.getName());
            if (creatorProperty == null
                    || creatorProperty.isInjectionOnly()
                    || creatorProperty.getInjectableValueId() != null
                    || covered.get(creatorProperty.getCreatorIndex())) {
                return null;
            }
            covered.set(creatorProperty.getCreatorIndex());
            creatorProperties[i] = creatorProperty;
            argumentIndexes[i] = creatorProperty.getCreatorIndex();
        }
        FastCreator fast = FastCreator.of(creator);
        if (fast == null || covered.cardinality() != fast.parameterCount()
                || covered.nextClearBit(0) != fast.parameterCount()) {
            return null;
        }
        return fast;
    }

    private Object deserializeWithFastCreator(JsonParser parser, DeserializationContext context) {
        SettableBeanProperty[] properties = _orderedProperties;
        Object[] arguments = new Object[fastCreator.parameterCount()];
        for (int i = 0; i < properties.length; i++) {
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                throw new PositionalCountException(parser, properties.length, String.valueOf(i));
            }
            SettableBeanProperty property = creatorProperties[i];
            if (property == null) {
                parser.skipChildren();
                continue;
            }
            try {
                arguments[argumentIndexes[i]] = property.deserialize(parser, context);
            }
            catch (Exception ex) {
                throw wrapAndThrow(ex, _beanType.getRawClass(), property.getName(), context);
            }
        }
        expectEnd(parser, properties.length);

        if (fastEnabled && !context.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            try {
                return fastCreator.create(arguments);
            }
            catch (Throwable fastFailure) {
                Object bean = createWithJackson(arguments, parser, context);
                fastEnabled = false;
                constructions.put(_beanType.getRawClass(), Construction.JACKSON);
                return bean;
            }
        }
        return createWithJackson(arguments, parser, context);
    }

    private Object createWithJackson(Object[] arguments, JsonParser parser, DeserializationContext context) {
        PropertyValueBuffer buffer = _propertyBasedCreator.startBuilding(parser, context, _objectIdReader);
        for (int i = 0; i < creatorProperties.length; i++) {
            if (creatorProperties[i] != null) {
                buffer.assignParameter(creatorProperties[i], arguments[argumentIndexes[i]]);
            }
        }
        try {
            return _propertyBasedCreator.build(context, buffer);
        }
        catch (Exception ex) {
            return wrapInstantiationProblem(context, ex);
        }
    }

    private Object deserializeWithSetters(JsonParser parser, DeserializationContext context) {
        Object bean = _valueInstantiator.createUsingDefault(context);
        parser.assignCurrentValue(bean);

        SettableBeanProperty[] properties = _orderedProperties;
        for (int i = 0; i < properties.length; i++) {
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                throw new PositionalCountException(parser, properties.length, String.valueOf(i));
            }
            SettableBeanProperty property = properties[i];
            if (property == null) {
                parser.skipChildren();
                continue;
            }
            try {
                property.deserializeAndSet(parser, context, bean);
            }
            catch (Exception ex) {
                throw wrapAndThrow(ex, bean, property.getName(), context);
            }
        }
        expectEnd(parser, properties.length);
        return bean;
    }

    private Object deserializeWithCreator(JsonParser parser, DeserializationContext context) {
        PropertyBasedCreator creator = _propertyBasedCreator;
        PropertyValueBuffer buffer = creator.startBuilding(parser, context, _objectIdReader);
        Object bean = null;

        SettableBeanProperty[] properties = _orderedProperties;
        for (int i = 0; i < properties.length; i++) {
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                throw new PositionalCountException(parser, properties.length, String.valueOf(i));
            }
            SettableBeanProperty property = properties[i];
            if (property == null) {
                parser.skipChildren();
                continue;
            }
            String name = property.getName();
            try {
                if (bean != null) {
                    property.deserializeAndSet(parser, context, bean);
                    continue;
                }
                SettableBeanProperty creatorProperty = creator.findCreatorProperty(name);
                if (creatorProperty == null) {
                    buffer.bufferProperty(property, property.deserialize(parser, context));
                }
                else if (creatorProperty.isInjectionOnly()) {
                    parser.skipChildren();
                }
                else if (buffer.assignParameter(creatorProperty, creatorProperty.deserialize(parser, context))) {
                    bean = creator.build(context, buffer);
                    parser.assignCurrentValue(bean);
                }
            }
            catch (Exception ex) {
                throw wrapAndThrow(ex, bean != null ? bean : _beanType.getRawClass(), name, context);
            }
        }
        expectEnd(parser, properties.length);

        if (bean == null) {
            try {
                bean = creator.build(context, buffer);
            }
            catch (Exception ex) {
                return wrapInstantiationProblem(context, ex);
            }
        }
        return bean;
    }

    private static void expectEnd(JsonParser parser, int expected) {
        if (parser.nextToken() != JsonToken.END_ARRAY) {
            throw new PositionalCountException(parser, expected, "more");
        }
    }
}
