package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 最小 YAML 标量发射器：把 Java 值编码为 YAML 标量文本（默认文件发射与运行时条目值写回共用）。
 *
 * <p>规则：数字/布尔/枚举/空值明文；字符串按 YAML 语义加引号（空串、前后空白、特殊首字符、
 * 含 {@code ": "} / {@code " #"}、会被隐式解析为非字符串的词如 true/数字/null/0x…、含引号或
 * 换行/制表符的用双引号转义）。{@link Map} 编码为 flow map（{@code {k: v}}）；{@link List}
 * 编码为 flow list（元素逐个经本编码器）。家族写回路线保持磁盘字节保真，发射器只产出单行
 * 标量、flow list 与 flow map。</p>
 */
public final class YamlScalar {

    private YamlScalar() {}

    private static final Pattern FORBIDDEN_PLAIN = Pattern.compile(
            "(?i)^(true|false|yes|no|on|off|null|~|[-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?"
                    + "|0x[0-9a-f]+|[-+]?\\.(inf|nan)"
                    + "|[0-9]{1,2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]+)?)?"
                    + "|[0-9]{4}-[0-9]{2}-[0-9]{2}([Tt ]?[0-9]{2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]+)?)?([Zz]|[+-][0-9]{2}:?[0-9]{2})?)?)$");

    private static final String SPECIAL_FIRST = "-?:,[]{}#&*!|>'\"%@`";

    /** 编码任意标量值；{@link Map} 编码为 flow map；{@link List}/{@link Set}/{@code T[]} 编码为
     *  flow list（元素逐个经本编码器）；{@link Optional} 空 → 空串、有值 → 内值编码。 */
    public static String encode(Object value) {
        if (value == null) {
            return "~";
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Number n) {
            return String.valueOf(n);
        }
        if (value instanceof Optional<?> o) {
            return o.isPresent() ? encode(o.get()) : "";
        }
        if (value instanceof List<?> l) {
            return encodeFlowList(l);
        }
        if (value instanceof Set<?> s) {
            return encodeFlowList(new java.util.ArrayList<>(s));
        }
        if (value != null && value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            List<Object> items = new java.util.ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                items.add(java.lang.reflect.Array.get(value, i));
            }
            return encodeFlowList(items);
        }
        if (value instanceof Map<?, ?> m) {
            return encodeFlowMap(m);
        }
        return encodeString(String.valueOf(value));
    }

    private static String encodeFlowList(List<?> l) {
        if (l.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(encode(l.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String encodeFlowMap(Map<?, ?> m) {
        if (m.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(encode(e.getKey())) // 数字键明文（5: …），字符串键经引号规则
                    .append(": ")
                    .append(encode(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String encodeString(String s) {
        if (s.isEmpty()) {
            return "\"\"";
        }
        if (s.indexOf('\n') >= 0 || s.indexOf('\t') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\'') >= 0) {
            return doubleQuoted(s);
        }
        if (needsQuote(s)) {
            return "'" + s + "'";
        }
        return s;
    }

    private static boolean needsQuote(String s) {
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if (first == ' ' || first == '\t' || last == ' ' || last == '\t') {
            return true;
        }
        if (SPECIAL_FIRST.indexOf(first) >= 0) {
            return true;
        }
        if (s.contains(": ") || s.endsWith(":") || s.contains(" #")) {
            return true;
        }
        return FORBIDDEN_PLAIN.matcher(s).matches();
    }

    private static String doubleQuoted(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
