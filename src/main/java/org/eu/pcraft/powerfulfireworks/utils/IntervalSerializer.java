package org.eu.pcraft.powerfulfireworks.utils;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

// 实现原始类型的序列化器
public final class IntervalSerializer implements TypeSerializer<Interval> {
    public static final IntervalSerializer INSTANCE = new IntervalSerializer();
    private IntervalSerializer() {}
    @Override
    public Interval<?> deserialize(Type type, ConfigurationNode node) throws SerializationException {
        Type elementType = getElementType(type);

        if (node.isList() && node.childrenList().size() >= 2) {
            return new Interval<>(
                    node.childrenList().get(0).get(elementType),
                    node.childrenList().get(1).get(elementType)
            );
        } else if (node.isMap()) {
            return new Interval<>(
                    node.node("minimum").get(elementType),
                    node.node("maximum").get(elementType)
            );
        } else {
            Object val = node.get(elementType);
            return new Interval<>(val, val);
        }
    }

    @Override
    public void serialize(Type type, @Nullable Interval obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.set(null);
            return;
        }

        Type elementType = getElementType(type);

        if (Objects.equals(obj.minimum, obj.maximum)) {
            node.set(elementType, obj.minimum);
        } else {
            node.node("minimum").set(elementType, obj.minimum);
            node.node("maximum").set(elementType, obj.maximum);
        }
    }

    private Type getElementType(Type type) {
        if (type instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0) {
            return pt.getActualTypeArguments()[0];
        }
        return Object.class;
    }
}