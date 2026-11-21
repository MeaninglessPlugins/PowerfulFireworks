package org.eu.pcraft.powerfulfireworks.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 脚本对象 → 纯数据请求的解析：缺字段用默认值，类型不对就报错。
 *
 * <p>这一层是脚本与运行时之间唯一的"翻译"，所有校验都发生在请求构造之前，
 * 宿主拿到的对象可以直接信任。
 */
final class ScriptRequests {

    private ScriptRequests() {
    }

    static FireworkRequest firework(Scriptable spec) {
        int count = (int) number(spec, "count", 1);
        int power = (int) number(spec, "power", 2);
        double velocity = number(spec, "velocity", 0);
        double launchChance = number(spec, "launchChance", 0.1);
        int fuse = (int) number(spec, "fuse", 40);
        boolean trail = bool(spec, "trail", false);
        boolean flicker = bool(spec, "flicker", false);

        String type = "";
        Object rawType = ScriptableObject.getProperty(spec, "type");
        if (rawType instanceof String name) {
            type = name.trim().toUpperCase(Locale.ROOT);
        }

        double[] offset = offset(spec);
        List<String> colors = colorList(spec, "colors");
        List<String> fade = colorList(spec, "fade");

        try {
            return new FireworkRequest(count, colors, fade, type, power, trail, flicker, velocity,
                    offset[0], offset[1], offset[2], launchChance, fuse);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    static ParticleRequest particle(Scriptable spec) {
        Object rawParticle = ScriptableObject.getProperty(spec, "particle");
        String particle = rawParticle instanceof String name ? name : null;

        int count = (int) number(spec, "count", 1);
        double speed = number(spec, "speed", 0);
        double size = number(spec, "size", 1);
        boolean force = bool(spec, "force", true);

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

        double[] spread = spread(spec);
        double[] offset = offset(spec);
        try {
            return new ParticleRequest(particle, count, spread[0], spread[1], spread[2],
                    speed, size, color, fade, offset[0], offset[1], offset[2], force);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    static PointSetRequest pointSet(Scriptable spec, Scriptable coords) {
        if (!coords.getClassName().equals("Array")) {
            throw Context.reportRuntimeError("坐标必须是一个数组");
        }
        int length = (int) Context.toNumber(ScriptableObject.getProperty(coords, "length"));
        List<double[]> points = new ArrayList<>(Math.min(length, PointSetRequest.MAX_POINTS));
        for (int i = 0; i < length; i++) {
            points.add(point(ScriptableObject.getProperty(coords, i), "第 " + (i + 1) + " 个坐标"));
        }
        try {
            return new PointSetRequest(emission(spec), points);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
    }

    /** 第一个参数既可以是粒子参数，也可以是烟花参数：有 particle 键就是粒子。 */
    static Emission emission(Scriptable spec) {
        Object particle = ScriptableObject.getProperty(spec, "particle");
        return particle instanceof String ? particle(spec) : firework(spec);
    }

    /**
     * 读一个坐标：数组必须正好三项，对象必须三个键都在。
     *
     * <p>这里比 offset 严格，因为少一个轴几乎一定是写错了，补 0 会让人以为写对了。
     */
    static double[] point(Object raw, String label) {
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

    static double coordinate(Scriptable object, String name, String label) {
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
    static List<String> colorList(Scriptable spec, String name) {
        List<String> colors = new ArrayList<>();
        Object raw = ScriptableObject.getProperty(spec, name);
        if (raw instanceof Scriptable list && list.getClassName().equals("Array")) {
            int length = (int) Context.toNumber(ScriptableObject.getProperty(list, "length"));
            for (int i = 0; i < length; i++) {
                colors.add(color(stringElement(list, i)));
            }
        } else if (raw instanceof String single) {
            colors.add(color(single));
        }
        return colors;
    }

    static String color(String color) {
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
    static double[] spread(Scriptable spec) {
        Object spread = ScriptableObject.getProperty(spec, "spread");
        if (spread instanceof Scriptable s && s.getClassName().equals("Array")) {
            return new double[]{element(s, 0, 0.0), element(s, 1, 0.0), element(s, 2, 0.0)};
        }
        double uniform = number(spec, "spread", 0);
        return new double[]{uniform, uniform, uniform};
    }

    /**
     * 读取 offset，支持 {@code [x, y, z]} 与 {@code {x, y, z}} 两种写法。
     *
     * <p>先按类名判断是不是数组：JS 的对象字面量同样是 Scriptable，
     * 只用类型判断会把 {@code {x, y, z}} 当成数组去取下标 0。
     */
    static double[] offset(Scriptable spec) {
        Object offset = ScriptableObject.getProperty(spec, "offset");
        if (offset instanceof Scriptable s && s.getClassName().equals("Array")) {
            return new double[]{element(s, 0, 0.0), element(s, 1, 0.0), element(s, 2, 0.0)};
        }
        if (offset instanceof Scriptable s) {
            return new double[]{
                    number(s, "x", 0),
                    number(s, "y", 0),
                    number(s, "z", 0)};
        }
        return new double[]{0, 0, 0};
    }

    /** 面朝方向 → x/z 分量；只接受四个水平朝向。 */
    static int[] faceMods(Scriptable spec) {
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

    static String requiredString(Scriptable object, String name) {
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

    static boolean bool(Scriptable object, String name, boolean fallback) {
        Object value = ScriptableObject.getProperty(object, name);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            return fallback;
        }
        return Context.toBoolean(value);
    }

    static double number(Scriptable object, String name, double fallback) {
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

    static double element(Scriptable array, int index, Double fallback) {
        Object value = ScriptableObject.getProperty(array, index);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            if (fallback == null) {
                throw Context.reportRuntimeError("数组第 " + index + " 项缺失");
            }
            return fallback;
        }
        return Context.toNumber(value);
    }

    static String stringElement(Scriptable array, int index) {
        Object value = ScriptableObject.getProperty(array, index);
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            throw Context.reportRuntimeError("数组第 " + index + " 项缺失");
        }
        return Context.toString(value);
    }
}
