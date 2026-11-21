package org.eu.pcraft.powerfulfireworks.script;

import static org.mozilla.javascript.ScriptableObject.putProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * {@code ctx.params} 与子脚本参数的 Java/JS 互转。
 *
 * <p>这里只做数据形状转换，不关心 {@code ctx.run}/{@code ctx.spawn} 的参数位置；
 * 那部分协议留在 {@link ScriptContext}。
 */
final class ScriptParams {

    private ScriptParams() {
    }

    /**
     * 把 Java 的 Map 变成脚本里能直接点取属性的普通对象。
     *
     * <p>不能用 {@code Context.javaToJS}：它给的是 {@code NativeJavaObject}，脚本里得写
     * {@code params.get('color')} 而不是 {@code params.color}。
     */
    static Scriptable toScriptable(Context cx, Scriptable scope, Map<String, ?> map) {
        Scriptable out = cx.newObject(scope);
        if (map == null) {
            return out;
        }
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            putProperty(out, entry.getKey(), toScriptableValue(cx, scope, entry.getValue()));
        }
        return out;
    }

    private static Object toScriptableValue(Context cx, Scriptable scope, Object raw) {
        if (raw == null || raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        if (raw instanceof Map<?, ?> map) {
            Scriptable out = cx.newObject(scope);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                putProperty(out, String.valueOf(entry.getKey()),
                        toScriptableValue(cx, scope, entry.getValue()));
            }
            return out;
        }
        if (raw instanceof Object[] array) {
            Object[] values = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                values[i] = toScriptableValue(cx, scope, array[i]);
            }
            return cx.newArray(scope, values);
        }
        if (raw instanceof Collection<?> collection) {
            Object[] values = collection.stream()
                    .map(value -> toScriptableValue(cx, scope, value))
                    .toArray();
            return cx.newArray(scope, values);
        }
        return String.valueOf(raw);
    }

    /** 脚本对象 → 纯数据 Map：子脚本收到的参数里不该出现 Rhino 对象。 */
    static Map<String, Object> toPlain(Scriptable object) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object key : object.getIds()) {
            Object value = key instanceof Integer index
                    ? object.get(index, object)
                    : object.get(String.valueOf(key), object);
            out.put(String.valueOf(key), toPlainValue(value));
        }
        return out;
    }

    private static Object toPlainValue(Object raw) {
        if (raw == null || raw == Undefined.instance || raw == Scriptable.NOT_FOUND) {
            return null;
        }
        if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        if (raw instanceof Scriptable scriptable) {
            if ("Array".equals(scriptable.getClassName())) {
                int length = (int) Context.toNumber(ScriptableObject.getProperty(scriptable, "length"));
                List<Object> out = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    out.add(toPlainValue(ScriptableObject.getProperty(scriptable, i)));
                }
                return out;
            }
            return toPlain(scriptable);
        }
        return String.valueOf(raw);
    }
}
