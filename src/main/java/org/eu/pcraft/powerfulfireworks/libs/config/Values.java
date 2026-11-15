package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 类型化读取助手。
 *
 * <p>与 PepperTreeCut 既有私有助手同名同签名（迁移机械、行为等价）：取值一律单层键访问
 * （{@code map.get(key)}），点号只出现在 issue 文案；类型不匹配/缺失 **静默回落默认值**
 * （不抛、不记 issue）；{@link #enumOf} 未知值记 WARN + 回落默认。</p>
 */
public final class Values {

    private Values() {}

    public static boolean boolOf(Map<String, Object> m, String key, boolean def) {
        return m.get(key) instanceof Boolean b ? b : def;
    }

    public static int intOf(Map<String, Object> m, String key, int def) {
        return m.get(key) instanceof Number n ? n.intValue() : def;
    }

    public static long longOf(Map<String, Object> m, String key, long def) {
        return m.get(key) instanceof Number n ? n.longValue() : def;
    }

    public static double doubleOf(Map<String, Object> m, String key, double def) {
        return m.get(key) instanceof Number n ? n.doubleValue() : def;
    }

    public static String stringOf(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    /**
     * 字符串列表归一：{@code null}/缺失 → 空列表；{@link Iterable} 内非 null 元素经
     * {@code String.valueOf}（数字、布尔元素同化成字符串，与 treecut 现状一致）；
     * 其它标量 → 单元素列表。
     */
    public static List<String> stringListOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Iterable<?> it)) {
            return List.of(String.valueOf(value));
        }
        List<String> out = new ArrayList<>();
        for (Object o : it) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /**
     * 枚举版 stringOf：未知值记一条 WARN {@link ConfigIssue}（path=key）并回落默认。
     */
    public static <E extends Enum<E>> E enumOf(
            Class<E> type, Map<String, Object> m, String key, E def, IssueCollector issues) {
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        String name = String.valueOf(v);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            issues.add(new ConfigIssue(IssueLevel.WARN, key, "未知枚举值 " + name + "，回落默认值 " + def, "修正配置"));
            return def;
        }
    }
}
