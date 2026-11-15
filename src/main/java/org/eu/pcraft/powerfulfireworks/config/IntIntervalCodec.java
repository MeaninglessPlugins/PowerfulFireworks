package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.ConfigCodec;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link Interval}&lt;Integer&gt; 的配置编解码。
 *
 * <p>装载时三种写法都认：单值（上下限相同）、{@code [min, max]}、{@code {minimum, maximum}}；
 * 生成默认文件时上下限相同写单值，否则写成 {@code {minimum, maximum}} 节，和旧版一致。</p>
 */
public final class IntIntervalCodec implements ConfigCodec<Interval<Integer>> {

    @Override
    public Object toConfig(Interval<Integer> value) {
        if (value == null) {
            return null;
        }
        if (Objects.equals(value.minimum, value.maximum)) {
            return value.minimum;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minimum", value.minimum);
        out.put("maximum", value.maximum);
        return out;
    }

    @Override
    public Interval<Integer> fromConfig(Object raw) {
        if (raw instanceof Number number) {
            int value = number.intValue();
            return new Interval<>(value, value);
        }
        if (raw instanceof List<?> list) {
            if (list.size() < 2) {
                throw new IllegalArgumentException("区间需要两个值: " + raw);
            }
            return new Interval<>(toInt(list.get(0)), toInt(list.get(1)));
        }
        if (raw instanceof Map<?, ?> map) {
            return new Interval<>(toInt(map.get("minimum")), toInt(map.get("maximum")));
        }
        throw new IllegalArgumentException("无法解析区间: " + raw);
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("区间值必须是数字: " + value);
    }
}
