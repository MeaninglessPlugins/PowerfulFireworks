package org.eu.pcraft.powerfulfireworks.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 脚本里的 {@code ctx} 对象。
 *
 * <p>目前只有最基础的一组：{@code tick}、{@code params}、{@code log}、{@code warn}、
 * {@code wait}、{@code rand}。发射、演出、观众等有副作用的 API 等运行时骨架落地后再加。
 */
final class ScriptContextObject {

    private ScriptContextObject() {
    }

    static Scriptable create(Context cx, Scriptable scope, ScriptHost host, RunStarter starter,
                             Scriptable paramObj, Random random) {
        Scriptable ctx = cx.newObject(scope);
        ScriptableObject.putProperty(ctx, "tick", 0);
        ScriptableObject.putProperty(ctx, "params", paramObj);

        ScriptableObject.putProperty(ctx, "log", function(scope, (c, s, t, args) -> {
            host.log(join(args));
            return Undefined.instance;
        }));
        ScriptableObject.putProperty(ctx, "warn", function(scope, (c, s, t, args) -> {
            host.warn(join(args));
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "wait", function(scope, (c, s, t, args) -> {
            double ticks = args.length == 0 ? 1 : Context.toNumber(args[0]);
            if (Double.isNaN(ticks) || ticks < 0) {
                throw Context.reportRuntimeError("ctx.wait(n) 需要非负数字");
            }
            // 返回对象而不是数字：yield 出来的值要能区分种类（将来还会有发射、演出指令），
            // 用 { wait: n } 这样的标签比裸数字好扩展。
            Scriptable out = c.newObject(s);
            ScriptableObject.putProperty(out, "wait", ticks);
            return out;
        }));

        ScriptableObject.putProperty(ctx, "firework", function(scope, (c, s, t, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.firework({...}) 需要一个对象参数");
            }
            host.firework(parseFirework(spec));
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "particle", function(scope, (c, s, t, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.particle({...}) 需要一个对象参数");
            }
            host.particle(parseParticle(spec));
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "points", function(scope, (c, s, t, args) -> {
            if (args.length < 2) {
                throw Context.reportRuntimeError("ctx.points(发射参数, 坐标数组) 需要两个参数");
            }
            if (!(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("第一个参数必须是发射参数对象，例如 { particle: \"dust\" }");
            }
            if (!(args[1] instanceof Scriptable coords)) {
                throw Context.reportRuntimeError("第二个参数必须是坐标数组，例如 [[0, 5, 0], [1, 5, 0]]");
            }
            host.points(parsePointSet(spec, coords));
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "run", function(scope, (c, s, t, args) -> {
            RunHandle handle = starter.start(scriptId(args, "ctx.run(id, params) 需要脚本 id"), params(args));
            if (handle == null) {
                throw Context.reportRuntimeError("没有脚本 " + scriptId(args, "ctx.run(id, params) 需要脚本 id")
                        + "，或它不能被当前运行调用（递归、层级过深或已被停掉）");
            }
            Scriptable out = c.newObject(s);
            // 带 join 标签的对象：父运行看到它就挂起，等子运行结束再继续
            ScriptableObject.putProperty(out, "join", handle);
            return out;
        }));

        ScriptableObject.putProperty(ctx, "spawn", function(scope, (c, s, t, args) -> {
            String id = scriptId(args, "ctx.spawn(id, params) 需要脚本 id");
            RunHandle handle = starter.start(id, params(args));
            if (handle == null) {
                throw Context.reportRuntimeError("没有脚本 " + id
                        + "，或它不能被当前运行调用（递归、层级过深或已被停掉）");
            }
            Scriptable out = c.newObject(s);
            ScriptableObject.putProperty(out, "id", id);
            return out;
        }));

        ScriptableObject.putProperty(ctx, "rand", randomObject(cx, scope, random));
        return ctx;
    }

    private static String scriptId(Object[] args, String usage) {
        if (args.length == 0) {
            throw Context.reportRuntimeError(usage);
        }
        String id = Context.toString(args[0]).strip();
        if (id.isEmpty()) {
            throw Context.reportRuntimeError(usage);
        }
        return id;
    }

    private static Map<String, Object> params(Object[] args) {
        if (args.length < 2 || !(args[1] instanceof Scriptable spec)) {
            return Map.of();
        }
        return plainParams(spec);
    }

    /**
     * 把 Java 的 Map 变成脚本里能直接点取属性的普通对象。
     *
     * <p>不能用 {@code Context.javaToJS}：它给的是 {@code NativeJavaObject}，脚本里得写
     * {@code params.get('color')} 而不是 {@code params.color}。
     */
    static Scriptable paramsObject(Context cx, Scriptable scope, Map<String, ?> map) {
        Scriptable out = cx.newObject(scope);
        if (map == null) {
            return out;
        }
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            ScriptableObject.putProperty(out, entry.getKey(), paramValue(cx, scope, entry.getValue()));
        }
        return out;
    }

    private static Object paramValue(Context cx, Scriptable scope, Object raw) {
        if (raw == null || raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        if (raw instanceof Map<?, ?> map) {
            Scriptable out = cx.newObject(scope);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                ScriptableObject.putProperty(out, String.valueOf(entry.getKey()),
                        paramValue(cx, scope, entry.getValue()));
            }
            return out;
        }
        if (raw instanceof Object[] array) {
            Object[] values = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                values[i] = paramValue(cx, scope, array[i]);
            }
            return cx.newArray(scope, values);
        }
        if (raw instanceof Collection<?> collection) {
            Object[] values = collection.stream().map(value -> paramValue(cx, scope, value)).toArray();
            return cx.newArray(scope, values);
        }
        return String.valueOf(raw);
    }

    /** 脚本对象 → 纯数据 Map：子脚本收到的参数里不该出现 Rhino 对象。 */
    static Map<String, Object> plainParams(Scriptable object) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object key : object.getIds()) {
            Object value = key instanceof Integer index
                    ? object.get(index, object)
                    : object.get(String.valueOf(key), object);
            out.put(String.valueOf(key), plainValue(value));
        }
        return out;
    }

    private static Object plainValue(Object raw) {
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
                    out.add(plainValue(ScriptableObject.getProperty(scriptable, i)));
                }
                return out;
            }
            return plainParams(scriptable);
        }
        return String.valueOf(raw);
    }

    /** 把脚本传来的对象转成纯数据的请求；字段缺失时用默认值，类型不对就报错。 */
    private static FireworkRequest parseFirework(Scriptable spec) {
        int count = (int) numberProperty(spec, "count", 1);
        int power = (int) numberProperty(spec, "power", 2);
        double velocity = numberProperty(spec, "velocity", 0);
        double launchChance = numberProperty(spec, "launchChance", 0.1);
        int fuse = (int) numberProperty(spec, "fuse", 40);
        boolean trail = booleanProperty(spec, "trail", false);
        boolean flicker = booleanProperty(spec, "flicker", false);

        String type = "";
        Object rawType = ScriptableObject.getProperty(spec, "type");
        if (rawType instanceof String name) {
            type = name.trim().toUpperCase(Locale.ROOT);
        }

        double[] offset = offsetOf(spec);
        List<String> colors = colorList(spec, "colors");
        List<String> fade = colorList(spec, "fade");

        try {
            return new FireworkRequest(count, colors, fade, type, power, trail, flicker, velocity,
                    offset[0], offset[1], offset[2], launchChance, fuse);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    private static ParticleRequest parseParticle(Scriptable spec) {
        Object rawParticle = ScriptableObject.getProperty(spec, "particle");
        String particle = rawParticle instanceof String name ? name : null;

        int count = (int) numberProperty(spec, "count", 1);
        double speed = numberProperty(spec, "speed", 0);
        double size = numberProperty(spec, "size", 1);
        boolean force = booleanProperty(spec, "force", true);

        String color = "#FFFFFF";
        Object rawColor = ScriptableObject.getProperty(spec, "color");
        if (rawColor instanceof String single) {
            color = single;
        }

        String fade = "";
        Object rawFade = ScriptableObject.getProperty(spec, "fade");
        if (rawFade instanceof String single) {
            fade = single;
        }

        double[] spread = spreadOf(spec);
        double[] offset = offsetOf(spec);
        try {
            return new ParticleRequest(particle, count, spread[0], spread[1], spread[2],
                    speed, size, color, fade, offset[0], offset[1], offset[2], force);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    private static PointSetRequest parsePointSet(Scriptable spec, Scriptable coords) {
        if (!coords.getClassName().equals("Array")) {
            throw Context.reportRuntimeError("坐标必须是一个数组");
        }
        int length = (int) Context.toNumber(ScriptableObject.getProperty(coords, "length"));
        List<double[]> points = new ArrayList<>(Math.min(length, PointSetRequest.MAX_POINTS));
        for (int i = 0; i < length; i++) {
            points.add(pointOf(ScriptableObject.getProperty(coords, i), i + 1));
        }
        try {
            return new PointSetRequest(parseEmission(spec), points);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    /** 第一个参数既可以是粒子参数，也可以是烟花参数：有 particle 键就是粒子。 */
    private static Emission parseEmission(Scriptable spec) {
        Object particle = ScriptableObject.getProperty(spec, "particle");
        return particle instanceof String ? parseParticle(spec) : parseFirework(spec);
    }

    /**
     * 读一个坐标：数组必须正好三项，对象必须三个键都在。
     *
     * <p>这里比 offset 严格，因为少一个轴几乎一定是写错了，补 0 会让人以为写对了。
     */
    private static double[] pointOf(Object raw, int index) {
        if (raw instanceof Scriptable array && array.getClassName().equals("Array")) {
            int length = (int) Context.toNumber(ScriptableObject.getProperty(array, "length"));
            if (length != 3) {
                throw Context.reportRuntimeError("第 " + index + " 个坐标必须是 [x, y, z]");
            }
            return new double[]{element(array, 0, null), element(array, 1, null), element(array, 2, null)};
        }
        if (raw instanceof Scriptable object) {
            return new double[]{
                    coordinate(object, "x", index),
                    coordinate(object, "y", index),
                    coordinate(object, "z", index)};
        }
        throw Context.reportRuntimeError("第 " + index + " 个坐标必须是 [x, y, z] 或 {x, y, z}");
    }

    private static double coordinate(Scriptable object, String name, int index) {
        Object value = ScriptableObject.getProperty(object, name);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            throw Context.reportRuntimeError("第 " + index + " 个坐标缺少 " + name);
        }
        double number = Context.toNumber(value);
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw Context.reportRuntimeError("第 " + index + " 个坐标的 " + name + " 不是数字");
        }
        return number;
    }

    /** 读取颜色：支持单个字符串或字符串数组。 */    private static List<String> colorList(Scriptable spec, String name) {
        List<String> colors = new ArrayList<>();
        Object raw = ScriptableObject.getProperty(spec, name);
        if (raw instanceof Scriptable list && list.getClassName().equals("Array")) {
            int length = (int) Context.toNumber(ScriptableObject.getProperty(list, "length"));
            for (int i = 0; i < length; i++) {
                colors.add(requireColor(stringElement(list, i)));
            }
        } else if (raw instanceof String single) {
            colors.add(requireColor(single));
        }
        return colors;
    }

    private static String requireColor(String color) {
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            throw Context.reportRuntimeError("颜色必须是 #RRGGBB 形式，当前 " + color);
        }
        return color;
    }

    /**
     * 读取粒子的散布半径。
     *
     * <p>写一个数字表示三个轴同值，写数组表示每个轴各一个值：
     * {@code spread: 0.8} 与 {@code spread: [0.5, 0, 0.5]} 都合法。
     */
    private static double[] spreadOf(Scriptable spec) {
        Object spread = ScriptableObject.getProperty(spec, "spread");
        if (spread instanceof Scriptable s && s.getClassName().equals("Array")) {
            return new double[]{element(s, 0, 0.0), element(s, 1, 0.0), element(s, 2, 0.0)};
        }
        double uniform = numberProperty(spec, "spread", 0);
        return new double[]{uniform, uniform, uniform};
    }

    /**
     * 读取 offset，支持 {@code [x, y, z]} 与 {@code {x, y, z}} 两种写法。
     *
     * <p>先按类名判断是不是数组：JS 的对象字面量同样是 Scriptable，
     * 只用类型判断会把 {@code {x, y, z}} 当成数组去取下标 0。
     */
    private static double[] offsetOf(Scriptable spec) {
        Object offset = ScriptableObject.getProperty(spec, "offset");
        if (offset instanceof Scriptable s && s.getClassName().equals("Array")) {
            return new double[]{element(s, 0, 0.0), element(s, 1, 0.0), element(s, 2, 0.0)};
        }
        if (offset instanceof Scriptable s) {
            return new double[]{
                    numberProperty(s, "x", 0),
                    numberProperty(s, "y", 0),
                    numberProperty(s, "z", 0)};
        }
        return new double[]{0, 0, 0};
    }

    private static boolean booleanProperty(Scriptable object, String name, boolean fallback) {
        Object value = ScriptableObject.getProperty(object, name);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            return fallback;
        }
        return Context.toBoolean(value);
    }

    private static double numberProperty(Scriptable object, String name, double fallback) {
        Object value = ScriptableObject.getProperty(object, name);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            return fallback;
        }
        double number = Context.toNumber(value);
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw Context.reportRuntimeError(name + " 需要一个数字");
        }
        return number;
    }

    private static double element(Scriptable array, int index, Double fallback) {
        Object value = ScriptableObject.getProperty(array, index);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            if (fallback == null) {
                throw Context.reportRuntimeError("数组第 " + index + " 项缺失");
            }
            return fallback;
        }
        return Context.toNumber(value);
    }

    private static String stringElement(Scriptable array, int index) {
        Object value = ScriptableObject.getProperty(array, index);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            throw Context.reportRuntimeError("数组第 " + index + " 项缺失");
        }
        return Context.toString(value);
    }

    private static Scriptable randomObject(Context cx, Scriptable scope, Random random) {
        Scriptable rand = cx.newObject(scope);
        ScriptableObject.putProperty(rand, "int", function(scope, (c, s, t, args) -> {
            if (args.length != 2) {
                throw Context.reportRuntimeError("ctx.rand.int(min, max) 需要两个参数");
            }
            int min = (int) Context.toNumber(args[0]);
            int max = (int) Context.toNumber(args[1]);
            if (max < min) {
                throw Context.reportRuntimeError("ctx.rand.int: max 不能小于 min");
            }
            return min + random.nextInt(max - min + 1);
        }));
        ScriptableObject.putProperty(rand, "float", function(scope, (c, s, t, args) -> {
            double min = args.length > 0 ? Context.toNumber(args[0]) : 0.0;
            double max = args.length > 1 ? Context.toNumber(args[1]) : 1.0;
            return min + random.nextDouble() * (max - min);
        }));
        ScriptableObject.putProperty(rand, "pick", function(scope, (c, s, t, args) -> {
            if (args.length == 0) {
                throw Context.reportRuntimeError("ctx.rand.pick(...) 至少需要一个候选值");
            }
            int idx = random.nextInt(args.length);
            return args[idx];
        }));
        ScriptableObject.putProperty(rand, "chance", function(scope, (c, s, t, args) -> {
            double p = args.length == 0 ? 0.5 : Context.toNumber(args[0]);
            return random.nextDouble() < p;
        }));
        return rand;
    }

    /** 让脚本能读到最新 tick。 */
    static void setTick(Scriptable ctx, long tick) {
        ScriptableObject.putProperty(ctx, "tick", (double) tick);
    }

    private static String join(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Context.toString(args[i]));
        }
        return sb.toString();
    }

    private static BaseFunction function(Scriptable scope, Fn body) {
        BaseFunction fn = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                return body.apply(cx, s, thisObj, args);
            }

            @Override
            public String getFunctionName() {
                return "";
            }
        };
        fn.setParentScope(scope);
        fn.setPrototype(ScriptableObject.getFunctionPrototype(scope));
        return fn;
    }

    @FunctionalInterface
    private interface Fn {
        Object apply(Context cx, Scriptable scope, Scriptable thisObj, Object[] args);
    }
}
