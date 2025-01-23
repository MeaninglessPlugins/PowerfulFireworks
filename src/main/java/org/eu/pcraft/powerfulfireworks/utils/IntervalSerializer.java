package org.eu.pcraft.powerfulfireworks.utils;

import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntervalSerializer<T> implements TypeSerializer<Interval> {
    public static final IntervalSerializer<Interval> INSTANCE = new IntervalSerializer<>();
    @Override
    public Interval<T> deserialize(Type type, ConfigurationNode node) throws SerializationException {
        Interval<T> interval = new Interval<>();
        @Nullable Object value = node.rawScalar();
        if(node.isList()){
            List<T> list = (List<T>) value;
            if (list != null && list.size() >= 2) {
                interval.minimum = list.get(0);
                interval.maximum = list.get(1);
            }
        }
        else if(node.isMap()){
            Map<String, T> map = (Map<String, T>)value;
            if(map!=null){
                interval.minimum = map.get("minimum");
                interval.maximum = map.get("maximum");
            }
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

    }
}
