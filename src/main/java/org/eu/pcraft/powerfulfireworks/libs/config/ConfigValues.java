package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 值级合法性信号（对齐 ConfigMe {@code PropertyValue.isValidInResource} 的二元表达并细化为三态）：
 * 每个声明条目在资源中的表示是否完全有效，供迁移/补键决策（"缺键补默认" vs "值不合法须重写"）。
 *
 * <p>三态语义（与 {@link ConfigSchema} 回落规则一致）：</p>
 * <ul>
 *   <li>{@link Status#PRESENT}——资源中存在且可直接使用（Optional 条目缺失视为 PRESENT）；
 *   <li>{@link Status#MISSING}——资源中缺失，值 = 模型默认（"值有效但不在资源中"，可触发补键）；
 *   <li>{@link Status#INVALID}——资源中存在但不可用（类型不符/越界/未知枚举/codec 失败/日期解析
 *       失败），已回落默认或夹紧，通常已伴随 WARN issue。
 * </ul>
 *
 * <p>本类型是"是否需要迁移/重写"的机器可读通道；缺键补默认的下沉动作仍是
 * {@link ConfigFileStore#upgrade(int)}（putIfAbsent，绝不覆盖管理员值）。二进制与既有
 * {@link IssueCollector} 正交：MISSING 不产生 issue（按设计静默回落），INVALID 伴随 WARN。</p>
 */
public final class ConfigValues {

    /** 单条目在资源中的表示状态。 */
    public enum Status {
        /** 资源中存在且完全有效。 */
        PRESENT,
        /** 资源中缺失；值 = 默认（有效但不在资源中）。 */
        MISSING,
        /** 资源中存在但不可用；已回落默认或夹紧。 */
        INVALID
    }

    /** 单条目：解析后的值 + 表示状态。 */
    public record Entry(Object value, Status status) {}

    private final Map<String, Entry> entries;

    ConfigValues(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** 全部条目（声明序）。 */
    public Map<String, Entry> entries() {
        return this.entries;
    }

    /** 指定键的状态；未知键返回 {@link Status#MISSING}。 */
    public Status status(String key) {
        Entry e = this.entries.get(key);
        return e == null ? Status.MISSING : e.status();
    }

    /** 指定键的解析值 + 状态；未知键返回 null + MISSING。 */
    public Entry entry(String key) {
        Entry e = this.entries.get(key);
        return e == null ? new Entry(null, Status.MISSING) : e;
    }

    /** 全部条目是否都在资源中完全有效（对齐 ConfigMe {@code areAllValuesValidInResource}）。 */
    public boolean allValidInResource() {
        for (Entry e : this.entries.values()) {
            if (e.status() != Status.PRESENT) {
                return false;
            }
        }
        return true;
    }

    /** 缺失键列表（声明序）。 */
    public List<String> missingKeys() {
        return keysWith(Status.MISSING);
    }

    /** 非法键列表（声明序）。 */
    public List<String> invalidKeys() {
        return keysWith(Status.INVALID);
    }

    private List<String> keysWith(Status s) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Entry> e : this.entries.entrySet()) {
            if (e.getValue().status() == s) {
                out.add(e.getKey());
            }
        }
        return List.copyOf(out);
    }
}
