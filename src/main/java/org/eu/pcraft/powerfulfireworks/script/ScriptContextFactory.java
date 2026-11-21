package org.eu.pcraft.powerfulfireworks.script;

import static org.eu.pcraft.powerfulfireworks.script.ScriptJs.function;
import static org.eu.pcraft.powerfulfireworks.script.ScriptJs.join;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 脚本里的 {@code ctx}：把 Java 侧的能力包成脚本函数。
 *
 * <p>请求解析在 {@link ScriptRequests}，观众构造与解析在 {@link ScriptAudience}，
 * 这里只负责接线、文字点阵与参数编解码。
 */
final class ScriptContextFactory {

    private ScriptContextFactory() {
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
            if (!Double.isFinite(ticks) || ticks < 1) {
                throw Context.reportRuntimeError("ctx.wait(n) 需要正的 tick 数");
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
            host.firework(ScriptRequests.firework(spec), args.length > 1 ? ScriptAudience.parse(args[1]) : null);
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "particle", function(scope, (c, s, t, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.particle({...}) 需要一个对象参数");
            }
            host.particle(ScriptRequests.particle(spec), args.length > 1 ? ScriptAudience.parse(args[1]) : null);
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
            host.points(ScriptRequests.pointSet(spec, coords),
                    args.length > 2 ? ScriptAudience.parse(args[2]) : null);
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
            return Undefined.instance;
        }));

        ScriptableObject.putProperty(ctx, "rand", randomObject(cx, scope, random));
        ScriptableObject.putProperty(ctx, "audience", ScriptAudience.object(cx, scope));
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
        String content = ScriptRequests.requiredString(spec, "text");
        String fontId = ScriptRequests.requiredString(spec, "font");
        double size = ScriptRequests.number(spec, "size", 1);
        int gap = (int) ScriptRequests.number(spec, "gap", 1);
        if (size <= 0) {
            throw Context.reportRuntimeError("size 必须大于 0");
        }
        if (gap < 0) {
            throw Context.reportRuntimeError("gap 不能为负");
        }
        if (ScriptRequests.bool(spec, "rotate", false)) {
            throw Context.reportRuntimeError("ctx.text 暂不支持 rotate（逐玩家朝向），请用 face 指定水平朝向");
        }

        BitmapFont font = host.font(fontId);
        if (font == null) {
            throw Context.reportRuntimeError("没有字体 " + fontId + "，可用："
                    + String.join(" / ", new TreeSet<>(host.fonts())));
        }

        ScriptRequests.Face face = ScriptRequests.face(spec);
        BitmapFont.CharBitmap bitmap = font.fromString(content, gap);
        List<BitmapFont.CharBitmap.Row> rows = bitmap.rows(size, face.xMod(), face.zMod());

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
}
