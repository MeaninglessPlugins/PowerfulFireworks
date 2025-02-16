package org.eu.pcraft.powerfulfireworks.utils;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public final class IntervalSerializer<T> implements TypeSerializer<Interval> {
    public static final IntervalSerializer<Interval> INSTANCE = new IntervalSerializer<>();
    @Override
    public Interval<T> deserialize(Type type, ConfigurationNode node) throws SerializationException {
        Interval<T> interval = new Interval<>();
        @Nullable Object value = node.raw();
        if(value instanceof List){
            List<T> list = (List<T>) value;
            if (list.size() >= 2) {
                interval.minimum = list.get(0);
                interval.maximum = list.get(1);
            }
        }
        else if(value instanceof Map){
            Map<String, T> map = (Map<String, T>)value;
            interval.minimum = map.get("minimum");
            interval.maximum = map.get("maximum");
        }
        else{
            if(value != null){
                interval.minimum = interval.maximum = (T) value;
            }
        }
        return interval;
    }

    @Override
    public void serialize(Type type, @Nullable Interval obj, ConfigurationNode node) throws SerializationException {
        if (obj.minimum == obj.maximum){
            node.set((Integer) obj.maximum);
        }
        Map<String, T> map = (Map<String, T>) Map.of("minimum", obj.minimum, "maximum", obj.maximum);
        node.set(map);
    }
}
