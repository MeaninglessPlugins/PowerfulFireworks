package org.eu.pcraft.powerfulfireworks.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;

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
            host.firework(parseFirework(spec), args.length > 1 ? parseAudience(args[1]) : null);
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "particle", function(scope, (c, s, t, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.particle({...}) 需要一个对象参数");
            }
            host.particle(parseParticle(spec), args.length > 1 ? parseAudience(args[1]) : null);
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
            host.points(parsePointSet(spec, coords), args.length > 2 ? parseAudience(args[2]) : null);
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "text", function(scope, (c, s, t, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.text({...}) 需要一个对象参数");
            }
            return textShape(host, c, s, spec);
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
        ScriptableObject.putProperty(ctx, "audience", audienceObject(cx, scope));
        return ctx;
    }

    /**
     * 把一段文字摊成点阵坐标，但不发射。
     *
     * <p>返回 {@code {width, height, rows: [{index, lift, points}]}}。点相对演出原点，
     * y 恒为 0——所有点从同一高度起飞，靠每行的 {@code lift} 在飞行末拉开高度；
     * 这样脚本可以决定每行用多少 tick、什么速度，甚至换粒子。
     */
    private static Scriptable textShape(ScriptHost host, Context cx, Scriptable scope, Scriptable spec) {
        String content = requiredString(spec, "text");
        String fontId = requiredString(spec, "font");
        double size = numberProperty(spec, "size", 1);
        int gap = (int) numberProperty(spec, "gap", 1);
        if (size <= 0) {
            throw Context.reportRuntimeError("size 必须大于 0");
        }
        if (gap < 0) {
            throw Context.reportRuntimeError("gap 不能为负");
        }
        if (booleanProperty(spec, "rotate", false)) {
            throw Context.reportRuntimeError("ctx.text 暂不支持 rotate（逐玩家朝向），请用 face 指定水平朝向");
        }

        BitmapFont font = host.font(fontId);
        if (font == null) {
            throw Context.reportRuntimeError("没有字体 " + fontId + "，可用："
                    + String.join(" / ", new TreeSet<>(host.fonts())));
        }

        int[] mods = faceMods(spec);
        BitmapFont.CharBitmap bitmap = font.fromString(content, gap);
        List<BitmapFont.CharBitmap.Row> rows = bitmap.rows(size, mods[0], mods[1]);

        Scriptable out = cx.newObject(scope);
        ScriptableObject.putProperty(out, "width", bitmap.width());
        ScriptableObject.putProperty(out, "height", bitmap.height());
        Object[] rowArray = new Object[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            BitmapFont.CharBitmap.Row row = rows.get(r);
            Scriptable item = cx.newObject(scope);
            ScriptableObject.putProperty(item, "index", row.index());
            ScriptableObject.putProperty(item, "lift", row.lift());
            Object[] points = new Object[row.points().size()];
            for (int p = 0; p < points.length; p++) {
                double[] point = row.points().get(p);
                points[p] = cx.newArray(scope, new Object[]{point[0], point[1], point[2]});
            }
            ScriptableObject.putProperty(item, "points", cx.newArray(scope, points));
            rowArray[r] = item;
        }
        ScriptableObject.putProperty(out, "rows", cx.newArray(scope, rowArray));
        return out;
    }

    /** 面朝方向 → x/z 分量；只接受四个水平朝向。 */
    private static int[] faceMods(Scriptable spec) {
        Object raw = ScriptableObject.getProperty(spec, "face");
        String face = raw == Scriptable.NOT_FOUND || raw == Undefined.instance
                ? "NORTH"
                : Context.toString(raw).trim().toUpperCase(Locale.ROOT);
        return switch (face) {
            case "EAST" -> new int[]{1, 0};
            case "WEST" -> new int[]{-1, 0};
            case "SOUTH" -> new int[]{0, 1};
            case "NORTH" -> new int[]{0, -1};
            default -> throw Context.reportRuntimeError(
                    "face 只支持水平朝向（EAST / WEST / NORTH / SOUTH），当前为 " + face);
        };
    }

    private static String requiredString(Scriptable object, String name) {
        Object value = ScriptableObject.getProperty(object, name);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            throw Context.reportRuntimeError(name + " 不能为空");
        }
        String text = Context.toString(value);
        if (text.isEmpty()) {
            throw Context.reportRuntimeError(name + " 不能为空");
        }
        return text;
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
            points.add(pointOf(ScriptableObject.getProperty(coords, i), "第 " + (i + 1) + " 个坐标"));
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
    private static double[] pointOf(Object raw, String label) {
        if (raw instanceof Scriptable array && array.getClassName().equals("Array")) {
            int length = (int) Context.toNumber(ScriptableObject.getProperty(array, "length"));
            if (length != 3) {
                throw Context.reportRuntimeError(label + "必须是 [x, y, z]");
            }
            return new double[]{element(array, 0, null), element(array, 1, null), element(array, 2, null)};
        }
        if (raw instanceof Scriptable object) {
            return new double[]{
                    coordinate(object, "x", label),
                    coordinate(object, "y", label),
                    coordinate(object, "z", label)};
        }
        throw Context.reportRuntimeError(label + "必须是 [x, y, z] 或 {x, y, z}");
    }

    private static double coordinate(Scriptable object, String name, String label) {
        Object value = ScriptableObject.getProperty(object, name);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            throw Context.reportRuntimeError(label + "缺少 " + name);
        }
        double number = Context.toNumber(value);
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw Context.reportRuntimeError(label + "的 " + name + " 不是数字");
        }
        return number;
    }

    /** 读取颜色：支持单个字符串或字符串数组。 */
    private static List<String> colorList(Scriptable spec, String name) {
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

    /** 观众构造器：全部返回纯数据对象，真正的名字在发射时才解析。 */
    private static Scriptable audienceObject(Context cx, Scriptable scope) {
        Scriptable audience = cx.newObject(scope);

        ScriptableObject.putProperty(audience, "default", function(scope, (c, s, t, args) ->
                audienceData(c, s, AudienceSpec.Kind.DEFAULT)));

        ScriptableObject.putProperty(audience, "all", function(scope, (c, s, t, args) ->
                audienceData(c, s, AudienceSpec.Kind.WORLD)));

        ScriptableObject.putProperty(audience, "none", function(scope, (c, s, t, args) ->
                audienceData(c, s, AudienceSpec.Kind.NONE)));

        ScriptableObject.putProperty(audience, "world", function(scope, (c, s, t, args) -> {
            Scriptable data = audienceData(c, s, AudienceSpec.Kind.WORLD);
            if (args.length > 0) {
                if (!(args[0] instanceof String name) || name.isBlank()) {
                    throw Context.reportRuntimeError("ctx.audience.world(名字) 需要一个世界名");
                }
                ScriptableObject.putProperty(data, "world", name.trim());
            }
            return data;
        }));

        ScriptableObject.putProperty(audience, "radius", function(scope, (c, s, t, args) -> {
            double radius = numberArg(args, 0, "ctx.audience.radius(r, ...) 需要半径");
            Scriptable data = audienceData(c, s, AudienceSpec.Kind.RADIUS);
            ScriptableObject.putProperty(data, "radius", radius);
            if (args.length > 1) {
                double[] center = pointOf(args[1], "半径的中心");
                ScriptableObject.putProperty(data, "x", center[0]);
                ScriptableObject.putProperty(data, "y", center[1]);
                ScriptableObject.putProperty(data, "z", center[2]);
                String world = args[1] instanceof Scriptable object ? stringProperty(object, "world") : null;
                if (world != null) {
                    ScriptableObject.putProperty(data, "world", world);
                }
            }
            return data;
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
            Scriptable data = audienceData(c, s, AudienceSpec.Kind.PLAYERS);
            ScriptableObject.putProperty(data, "names", names);
            return data;
        }));

        return audience;
    }

    private static Scriptable audienceData(Context cx, Scriptable scope, AudienceSpec.Kind kind) {
        Scriptable data = cx.newObject(scope);
        ScriptableObject.putProperty(data, "kind", kind.name().toLowerCase(Locale.ROOT));
        return data;
    }

    /** 把脚本构造的观众对象转成 {@link AudienceSpec}；没传时返回 null，表示用默认规则。 */
    private static AudienceSpec parseAudience(Object raw) {
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
                case "players" -> AudienceSpec.players(namesOf(data));
                case "radius" -> radiusSpec(data);
                default -> throw Context.reportRuntimeError("不认识的观众类型：" + kind);
            };
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    private static AudienceSpec radiusSpec(Scriptable data) {
        double radius = numberProperty(data, "radius", -1);
        if (radius < 0) {
            throw Context.reportRuntimeError("ctx.audience.radius(r, ...) 需要半径");
        }
        boolean x = hasProperty(data, "x");
        boolean y = hasProperty(data, "y");
        boolean z = hasProperty(data, "z");
        if (!x && !y && !z) {
            return new AudienceSpec(AudienceSpec.Kind.RADIUS, radius, null, null, null,
                    stringProperty(data, "world"), List.of());
        }
        if (!(x && y && z)) {
            throw Context.reportRuntimeError("观众中心需要同时给出 x、y、z");
        }
        return new AudienceSpec(AudienceSpec.Kind.RADIUS, radius,
                numberProperty(data, "x", 0), numberProperty(data, "y", 0), numberProperty(data, "z", 0),
                stringProperty(data, "world"), List.of());
    }

    private static List<String> namesOf(Scriptable data) {
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

    private static boolean hasProperty(Scriptable object, String name) {
        Object value = ScriptableObject.getProperty(object, name);
        return value != Scriptable.NOT_FOUND && value != Undefined.instance;
    }

    private static String stringProperty(Scriptable object, String name) {
        Object value = ScriptableObject.getProperty(object, name);
        return value instanceof String text ? text : null;
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
