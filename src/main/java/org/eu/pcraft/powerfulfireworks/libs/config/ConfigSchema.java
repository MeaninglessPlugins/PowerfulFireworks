package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 声明式 schema 内核（package-private）：条目声明（键/类型/默认值/校验/注释）、类型化读取与
 * 模板一致性守卫。
 *
 * <p>取值语义：类型不匹配或缺失时静默回落默认值（不记 issue）；越界与未知枚举记 WARN 并回落
 * 默认；重复键声明直接拒绝。键为点号路径（如 {@code "profile.maxLogs"}），单层键是其特例；
 * 本类型不进入 japicmp 守护面，公开 API 见 {@link Bindings} 与 {@link ConfigModel} 注解族。</p>
 */
final class ConfigSchema {

    /** 条目值类型。 */
    enum ValueType {
        BOOL,
        INT,
        LONG,
        DOUBLE,
        STRING,
        ENUM,
        LIST,
        MAP,
        OPTIONAL,
        SET,
        ARRAY,
        TEMPORAL,
        CODEC
    }

    /** 单条目：点号路径键 + 类型 + 默认值 + 可选枚举类/时间类/范围守卫/注释/自定义 codec。 */
    record Entry(
            String key,
            ValueType type,
            Object def,
            Class<? extends Enum<?>> enumClass,
            Class<?> temporalClass,
            Predicate<Number> bounds,
            double min,
            double max,
            boolean clamp,
            List<String> comments,
            ConfigCodec<?> codec) {

        public Entry {
            comments = List.copyOf(comments);
        }
    }

    /** 解析结果：值 + 资源表示状态（供 {@link ConfigValues} 值级合法性信号）。 */
    record Resolved(Object value, ConfigValues.Status status) {}

    private final List<Entry> entries;
    private final Set<String> knownKeys;
    private final Map<String, Object> defaults;

    private ConfigSchema(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Set<String> keys = new HashSet<>();
        Map<String, Object> d = new LinkedHashMap<>();
        for (Entry e : entries) {
            keys.add(e.key());
            d.put(e.key(), e.def());
        }
        this.knownKeys = Set.copyOf(keys);
        this.defaults = Collections.unmodifiableMap(d);
    }

    /** 条目序（声明序；发射默认文件按此遍历）。 */
    List<Entry> entries() {
        return this.entries;
    }

    /** 全部键（点号路径，无序视图）。 */
    Set<String> knownKeys() {
        return this.knownKeys;
    }

    /** 默认值表（点号路径 → 默认值）。 */
    Map<String, Object> defaults() {
        return this.defaults;
    }

    /**
     * 类型化读取：磁盘 map（YamlMap 产物）→ 解析后的值视图 + 每条目资源表示状态；越界/未知枚举
     * 记 issue 并回落默认。
     */
    SchemaValues read(Map<String, Object> rootMap, IssueCollector issues) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, ConfigValues.Status> status = new LinkedHashMap<>();
        for (Entry e : this.entries) {
            Object raw = rawAt(rootMap, e.key());
            Resolved r = resolve(e, raw, issues);
            values.put(e.key(), r.value());
            status.put(e.key(), r.status());
        }
        return new SchemaValues(values, status);
    }

    private Resolved resolve(Entry e, Object raw, IssueCollector issues) {
        if (e.type() == ValueType.OPTIONAL) {
            // 对齐 ConfigMe OptionalProperty：缺失/空 = 有效表示（PRESENT）；值 = 字段默认；
            // 存在即包裹（元素级强制在绑定层）。
            if (raw == null) {
                return new Resolved(e.def(), ConfigValues.Status.PRESENT);
            }
            return new Resolved(Optional.ofNullable(raw), ConfigValues.Status.PRESENT);
        }
        if (raw == null) {
            return new Resolved(e.def(), ConfigValues.Status.MISSING);
        }
        switch (e.type()) {
            case BOOL:
                // 类型不符时静默回落（不记 issue），但状态记为 INVALID，供迁移判断是否需要重写。
                return raw instanceof Boolean b
                        ? new Resolved(b, ConfigValues.Status.PRESENT)
                        : new Resolved(e.def(), ConfigValues.Status.INVALID);
            case INT:
                if (raw instanceof Number n) {
                    // S15：非有限数（.nan/.inf）永不进入运行值——在收窄为 int 前拦截。
                    if (!Double.isFinite(n.doubleValue())) {
                        return invalid(e, issues, "配置值非有限数（NaN/Infinity），已回落默认值 " + e.def());
                    }
                    int v = n.intValue();
                    if (e.bounds() != null && !e.bounds().test(v)) {
                        if (e.clamp()) {
                            int clamped = (int) clamp(v, e.min(), e.max());
                            return clampedValue(e, issues, clamped, "配置值 " + v + " 超出允许范围，已修正为 " + clamped);
                        }
                        return invalid(e, issues, "配置值 " + v + " 超出允许范围，已回落默认值 " + e.def());
                    }
                    return new Resolved(v, ConfigValues.Status.PRESENT);
                }
                return new Resolved(e.def(), ConfigValues.Status.INVALID);
            case LONG:
                if (raw instanceof Number n) {
                    if (!Double.isFinite(n.doubleValue())) {
                        return invalid(e, issues, "配置值非有限数（NaN/Infinity），已回落默认值 " + e.def());
                    }
                    long v = n.longValue();
                    if (e.bounds() != null && !e.bounds().test(v)) {
                        if (e.clamp()) {
                            long clamped = (long) clamp(v, e.min(), e.max());
                            return clampedValue(e, issues, clamped, "配置值 " + v + " 超出允许范围，已修正为 " + clamped);
                        }
                        return invalid(e, issues, "配置值 " + v + " 超出允许范围，已回落默认值 " + e.def());
                    }
                    return new Resolved(v, ConfigValues.Status.PRESENT);
                }
                return new Resolved(e.def(), ConfigValues.Status.INVALID);
            case DOUBLE:
                if (raw instanceof Number n) {
                    double v = n.doubleValue();
                    if (!Double.isFinite(v)) {
                        return invalid(e, issues, "配置值非有限数（NaN/Infinity），已回落默认值 " + e.def());
                    }
                    if (e.bounds() != null && !e.bounds().test(v)) {
                        if (e.clamp()) {
                            double clamped = clamp(v, e.min(), e.max());
                            return clampedValue(e, issues, clamped, "配置值 " + v + " 超出允许范围，已修正为 " + clamped);
                        }
                        return invalid(e, issues, "配置值 " + v + " 超出允许范围，已回落默认值 " + e.def());
                    }
                    return new Resolved(v, ConfigValues.Status.PRESENT);
                }
                return new Resolved(e.def(), ConfigValues.Status.INVALID);
            case STRING:
                return new Resolved(String.valueOf(raw), ConfigValues.Status.PRESENT);
            case ENUM:
                if (e.enumClass() != null) {
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Enum<?> v = Enum.valueOf(
                                (Class) e.enumClass(),
                                String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
                        return new Resolved(v, ConfigValues.Status.PRESENT);
                    } catch (IllegalArgumentException ex) {
                        return invalid(e, issues, "未知枚举值 " + raw + "，已回落默认值 " + e.def());
                    }
                }
                return new Resolved(e.def(), ConfigValues.Status.INVALID);
            case LIST:
                return raw instanceof List<?> l
                        ? new Resolved(l, ConfigValues.Status.PRESENT)
                        : new Resolved(e.def(), ConfigValues.Status.INVALID);
            case MAP:
                return raw instanceof Map<?, ?> m
                        ? new Resolved(m, ConfigValues.Status.PRESENT)
                        : new Resolved(e.def(), ConfigValues.Status.INVALID);
            case SET:
                return raw instanceof java.util.Collection<?> c
                        ? new Resolved(new ArrayList<>(c), ConfigValues.Status.PRESENT)
                        : new Resolved(e.def(), ConfigValues.Status.INVALID);
            case ARRAY:
                return raw instanceof List<?> l2
                        ? new Resolved(l2, ConfigValues.Status.PRESENT)
                        : new Resolved(e.def(), ConfigValues.Status.INVALID);
            case TEMPORAL:
                if (e.temporalClass() == null) {
                    return new Resolved(e.def(), ConfigValues.Status.INVALID);
                }
                Object parsed = TemporalValues.parse(raw, e.temporalClass());
                return parsed != null
                        ? new Resolved(parsed, ConfigValues.Status.PRESENT)
                        : invalid(e, issues, "无法解析时间值 " + raw + "，已回落默认值 " + e.def());
            case CODEC:
                if (raw == null || e.codec() == null) {
                    return new Resolved(e.def(), ConfigValues.Status.MISSING);
                }
                try {
                    return new Resolved(e.codec().fromConfig(raw), ConfigValues.Status.PRESENT);
                } catch (RuntimeException ex) {
                    // 与家族 Values 语义一致：非法输入静默回落字段默认（不记 issue），状态记 INVALID
                    return new Resolved(e.def(), ConfigValues.Status.INVALID);
                }
            default:
                return new Resolved(e.def(), ConfigValues.Status.MISSING);
        }
    }

    /** 非法回落：记 WARN + INVALID 状态。 */
    private static Resolved invalid(Entry e, IssueCollector issues, String message) {
        issues.add(new ConfigIssue(IssueLevel.WARN, e.key(), message, "修正配置"));
        return new Resolved(e.def(), ConfigValues.Status.INVALID);
    }

    /** 夹紧修正：记 WARN + INVALID 状态，值保留夹紧结果（而非默认）。 */
    private static Resolved clampedValue(Entry e, IssueCollector issues, Object value, String message) {
        issues.add(new ConfigIssue(IssueLevel.WARN, e.key(), message, "修正配置"));
        return new Resolved(value, ConfigValues.Status.INVALID);
    }

    private static double clamp(double v, double min, double max) {
        double out = v;
        if (!Double.isNaN(min) && out < min) {
            out = min;
        }
        if (!Double.isNaN(max) && out > max) {
            out = max;
        }
        return out;
    }

    /** 按点号路径取原始值；路径中缺失段/非映射返回 null（null 与缺失等价，回落默认）。 */
    static Object rawAt(Map<String, Object> rootMap, String dotted) {
        Object cur = rootMap;
        for (String seg : dotted.split("\\.")) {
            if (!(cur instanceof Map<?, ?> mm) || !mm.containsKey(seg)) {
                return null;
            }
            cur = mm.get(seg);
        }
        return cur;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    static Builder builder() {
        return new Builder();
    }

    /** 声明式构建器（内核/测试/注解层共用）；重复键拒绝。 */
    static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();

        public Builder bool(String key, boolean def) {
            add(key, ValueType.BOOL, def, null);
            return this;
        }

        public Builder intField(String key, int def, Predicate<Integer> bounds) {
            add(key, ValueType.INT, def, n -> bounds.test(n.intValue()));
            return this;
        }

        public Builder longField(String key, long def) {
            add(key, ValueType.LONG, def, null);
            return this;
        }

        public Builder doubleField(String key, double def) {
            add(key, ValueType.DOUBLE, def, null);
            return this;
        }

        public Builder string(String key, String def) {
            add(key, ValueType.STRING, def, null);
            return this;
        }

        public Builder enumField(String key, Class<? extends Enum<?>> type, Enum<?> def) {
            field(key, ValueType.ENUM, def, type, null, null, Double.NaN, Double.NaN, false, List.of());
            return this;
        }

        public Builder list(String key) {
            add(key, ValueType.LIST, List.of(), null);
            return this;
        }

        /**
         * 全参数添加（含 temporalClass：TEMPORAL 条目的目标时间类；其余类型为 null）。
         * 注解绑定层使用。
         */
        public Builder field(
                String key,
                ValueType type,
                Object def,
                Class<? extends Enum<?>> enumClass,
                Class<?> temporalClass,
                Predicate<Number> bounds,
                double min,
                double max,
                boolean clamp,
                List<String> comments) {
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate schema key: " + key);
            }
            ConfigPaths.checkKey(key);
            this.entries.add(
                    new Entry(key, type, def, enumClass, temporalClass, bounds, min, max, clamp, comments, null));
            return this;
        }

        /** 自定义 codec 条目（注解绑定层使用）。 */
        public Builder codecField(String key, Object def, ConfigCodec<?> codec, List<String> comments) {
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate schema key: " + key);
            }
            ConfigPaths.checkKey(key);
            this.entries.add(new Entry(
                    key, ValueType.CODEC, def, null, null, null, Double.NaN, Double.NaN, false, comments, codec));
            return this;
        }

        private void add(String key, ValueType type, Object def, Predicate<Number> bounds) {
            field(key, type, def, null, null, bounds, Double.NaN, Double.NaN, false, List.of());
        }

        public ConfigSchema build() {
            return new ConfigSchema(this.entries);
        }
    }
}
