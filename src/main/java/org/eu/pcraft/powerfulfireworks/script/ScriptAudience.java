package org.eu.pcraft.powerfulfireworks.script;

import static org.eu.pcraft.powerfulfireworks.script.ScriptJs.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * {@code ctx.audience.*} 的构造器与反向解析。
 *
 * <p>脚本侧拿到的是纯数据对象（{@code {kind, radius, x, y, z, world, names}}），
 * 没有任何玩家引用：真正的名单在发射那一刻由宿主解析，所以进服、走开、改视距都会自然生效；
 * 构造这些规则本身不需要服务器。
 */
final class ScriptAudience {

    private ScriptAudience() {
    }

    static Scriptable object(Context cx, Scriptable scope) {
        Scriptable audience = cx.newObject(scope);

        ScriptableObject.putProperty(audience, "default", function(scope, (c, s, t, args) ->
                data(c, s, AudienceSpec.Kind.DEFAULT)));

        ScriptableObject.putProperty(audience, "all", function(scope, (c, s, t, args) ->
                data(c, s, AudienceSpec.Kind.WORLD)));

        ScriptableObject.putProperty(audience, "none", function(scope, (c, s, t, args) ->
                data(c, s, AudienceSpec.Kind.NONE)));

        ScriptableObject.putProperty(audience, "world", function(scope, (c, s, t, args) -> {
            Scriptable out = data(c, s, AudienceSpec.Kind.WORLD);
            if (args.length > 0) {
                if (!(args[0] instanceof String name) || name.isBlank()) {
                    throw Context.reportRuntimeError("ctx.audience.world(名字) 需要一个世界名");
                }
                ScriptableObject.putProperty(out, "world", name.trim());
            }
            return out;
        }));

        ScriptableObject.putProperty(audience, "radius", function(scope, (c, s, t, args) -> {
            double radius = numberArg(args, 0, "ctx.audience.radius(r, ...) 需要半径");
            Scriptable out = data(c, s, AudienceSpec.Kind.RADIUS);
            ScriptableObject.putProperty(out, "radius", radius);
            if (args.length > 1) {
                double[] center = ScriptRequests.point(args[1], "半径的中心");
                ScriptableObject.putProperty(out, "x", center[0]);
                ScriptableObject.putProperty(out, "y", center[1]);
                ScriptableObject.putProperty(out, "z", center[2]);
            }
            return out;
        }));

        ScriptableObject.putProperty(audience, "players", function(scope, (c, s, t, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable list)
                    || !"Array".equals(list.getClassName())) {
                throw Context.reportRuntimeError("ctx.audience.players([...]) 需要一个名字数组");
            }
            int length = (int) Context.toNumber(ScriptableObject.getProperty(list, "length"));
            if (length == 0) {
                throw Context.reportRuntimeError("ctx.audience.players([...]) 至少写一个名字");
            }
            Scriptable names = (Scriptable) c.newArray(s, length);
            for (int i = 0; i < length; i++) {
                ScriptableObject.putProperty(names, i, Context.toString(ScriptableObject.getProperty(list, i)));
            }
            Scriptable out = data(c, s, AudienceSpec.Kind.PLAYERS);
            ScriptableObject.putProperty(out, "names", names);
            return out;
        }));

        return audience;
    }

    /** 把脚本构造的观众对象转成 {@link AudienceSpec}；没传时返回 null，表示用默认规则。 */
    static AudienceSpec parse(Object raw) {
        if (!(raw instanceof Scriptable data)) {
            throw Context.reportRuntimeError("观众要用 ctx.audience.xxx() 构造，不能直接写 " + Context.toString(raw));
        }
        Object rawKind = ScriptableObject.getProperty(data, "kind");
        String kind = rawKind instanceof String text ? text.trim().toLowerCase(Locale.ROOT) : "";
        try {
            return switch (kind) {
                case "default" -> AudienceSpec.vanilla();
                case "none" -> AudienceSpec.none();
                case "world" -> AudienceSpec.world(stringProperty(data, "world"));
                case "players" -> AudienceSpec.players(names(data));
                case "radius" -> radius(data);
                default -> throw Context.reportRuntimeError("不认识的观众类型：" + kind);
            };
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    private static Scriptable data(Context cx, Scriptable scope, AudienceSpec.Kind kind) {
        Scriptable out = cx.newObject(scope);
        ScriptableObject.putProperty(out, "kind", kind.name().toLowerCase(Locale.ROOT));
        return out;
    }

    private static AudienceSpec radius(Scriptable data) {
        double radius = ScriptRequests.number(data, "radius", -1);
        if (radius < 0) {
            throw Context.reportRuntimeError("ctx.audience.radius(r, ...) 需要半径");
        }
        boolean x = has(data, "x");
        boolean y = has(data, "y");
        boolean z = has(data, "z");
        if (!x && !y && !z) {
            return new AudienceSpec(AudienceSpec.Kind.RADIUS, radius, null, null, null,
                    stringProperty(data, "world"), List.of());
        }
        if (!(x && y && z)) {
            throw Context.reportRuntimeError("观众中心需要同时给出 x、y、z");
        }
        return new AudienceSpec(AudienceSpec.Kind.RADIUS, radius,
                ScriptRequests.number(data, "x", 0),
                ScriptRequests.number(data, "y", 0),
                ScriptRequests.number(data, "z", 0),
                stringProperty(data, "world"), List.of());
    }

    private static List<String> names(Scriptable data) {
        Object raw = ScriptableObject.getProperty(data, "names");
        if (!(raw instanceof Scriptable list) || !"Array".equals(list.getClassName())) {
            throw Context.reportRuntimeError("ctx.audience.players([...]) 需要一个名字数组");
        }
        int length = (int) Context.toNumber(ScriptableObject.getProperty(list, "length"));
        List<String> names = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            names.add(Context.toString(ScriptableObject.getProperty(list, i)));
        }
        return names;
    }

    private static double numberArg(Object[] args, int index, String message) {
        if (args.length <= index) {
            throw Context.reportRuntimeError(message);
        }
        double value = Context.toNumber(args[index]);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw Context.reportRuntimeError(message);
        }
        return value;
    }

    private static boolean has(Scriptable object, String name) {
        Object value = ScriptableObject.getProperty(object, name);
        return value != Scriptable.NOT_FOUND && value != Undefined.instance;
    }

    private static String stringProperty(Scriptable object, String name) {
        Object value = ScriptableObject.getProperty(object, name);
        return value instanceof String text ? text : null;
    }
}
