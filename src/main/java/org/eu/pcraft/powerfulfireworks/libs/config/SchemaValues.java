package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 类型化读取结果（package-private）：{@link ConfigSchema#read} 解析后的值视图 + 资源表示状态。 */
final class SchemaValues {

    private final Map<String, Object> values;
    private final Map<String, ConfigValues.Status> status;

    SchemaValues(Map<String, Object> values, Map<String, ConfigValues.Status> status) {
        this.values = values;
        this.status = status;
    }

    /** 值级合法性信号视图（声明序；值 = 解析值 + 资源表示状态）。 */
    ConfigValues configValues() {
        Map<String, ConfigValues.Entry> es = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : this.values.entrySet()) {
            ConfigValues.Status s = this.status.getOrDefault(e.getKey(), ConfigValues.Status.MISSING);
            es.put(e.getKey(), new ConfigValues.Entry(e.getValue(), s));
        }
        return new ConfigValues(es);
    }

    boolean bool(String key) {
        return (Boolean) this.values.get(key);
    }

    int intValue(String key) {
        return (Integer) this.values.get(key);
    }

    long longValue(String key) {
        return (Long) this.values.get(key);
    }

    double doubleValue(String key) {
        return (Double) this.values.get(key);
    }

    String string(String key) {
        return (String) this.values.get(key);
    }

    <E extends Enum<E>> E enumValue(String key, Class<E> type) {
        return type.cast(this.values.get(key));
    }

    List<?> list(String key) {
        return (List<?>) this.values.get(key);
    }

    /** 原始解析值（注解绑定层按字段类型自行取用）。 */
    Object raw(String key) {
        return this.values.get(key);
    }
}
