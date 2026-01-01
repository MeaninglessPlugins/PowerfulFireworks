package org.eu.pcraft.powerfulfireworks.utils;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

public final class IntervalSerializer implements TypeSerializer<Interval<?>> {
    public static final IntervalSerializer INSTANCE = new IntervalSerializer();

    private IntervalSerializer() {}

    @Override
    public Interval<?> deserialize(Type type, ConfigurationNode node) throws SerializationException {
        // 获取 Interval<T> 中的 T 类型
        Type elementType = getElementType(type);
        Interval<Object> interval = new Interval<>();

        if (node.isList()) {
            List<? extends ConfigurationNode> children = node.childrenList();
            if (children.size() >= 2) {
                interval.minimum = children.get(0).get(elementType);
                interval.maximum = children.get(1).get(elementType);
            }
        } else if (node.isMap()) {
            interval.minimum = node.node("minimum").get(elementType);
            interval.maximum = node.node("maximum").get(elementType);
        } else if (!node.empty()) {
            // 单个值的情况：min = max
            Object val = node.get(elementType);
            interval.minimum = val;
            interval.maximum = val;
        }

        return interval;
    }

    @Override
    public void serialize(Type type, @Nullable Interval<?> obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.set(null);
            return;
        }

        Type elementType = getElementType(type);

        // 如果最小值和最大值相等，则简化为一个值
        if (Objects.equals(obj.minimum, obj.maximum)) {
            node.set(elementType, obj.minimum);
        } else {
            // 否则序列化为 Map 结构
            node.node("minimum").set(elementType, obj.minimum);
            node.node("maximum").set(elementType, obj.maximum);
        }
    }

    // 辅助方法：提取泛型参数 T
    private Type getElementType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        return Object.class;
    }
}