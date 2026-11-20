package org.eu.pcraft.powerfulfireworks.script;

import java.util.List;

/**
 * 一次烟花发射请求。
 *
 * <p>脚本只描述"要什么"，宿主负责造物品与发包。构造时校验，宿主可以直接信任这些值。
 *
 * @param count        火箭数量，每支是独立实体
 * @param colors       爆炸颜色，空表示随机两色
 * @param fade         爆炸后的渐隐颜色，可为空
 * @param type         爆炸形状，空表示随机；取值 BALL / BALL_LARGE / STAR / BURST / CREEPER
 * @param power        引信长度 0..5，决定升空高度
 * @param trail        是否带拖尾粒子
 * @param flicker      是否闪烁
 * @param velocity     额外初速度（格/tick），叠加在原版上升曲线上
 * @param offsetX      相对原点的水平偏移
 * @param offsetY      相对原点的垂直偏移
 * @param offsetZ      相对原点的水平偏移
 * @param launchChance 发射声的播放概率
 * @param fuse         从升空到爆炸的 tick 数；到点后由宿主发爆炸包并清掉实体
 */
public record FireworkRequest(
        int count,
        List<String> colors,
        List<String> fade,
        String type,
        int power,
        boolean trail,
        boolean flicker,
        double velocity,
        double offsetX,
        double offsetY,
        double offsetZ,
        double launchChance,
        int fuse) implements Emission {

    /** 原版存在的五种爆炸形状。 */
    private static final List<String> TYPES = List.of("BALL", "BALL_LARGE", "STAR", "BURST", "CREEPER");

    public FireworkRequest {
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("count 必须在 1..64 之间，当前 " + count);
        }
        if (power < 0 || power > 5) {
            throw new IllegalArgumentException("power 必须在 0..5 之间，当前 " + power);
        }
        if (launchChance < 0 || launchChance > 1) {
            throw new IllegalArgumentException("launchChance 必须在 0..1 之间，当前 " + launchChance);
        }
        if (fuse < 1 || fuse > 200) {
            throw new IllegalArgumentException("fuse 必须在 1..200 之间，当前 " + fuse);
        }
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null，随机请用空字符串");
        }
        if (!type.isEmpty() && !TYPES.contains(type)) {
            throw new IllegalArgumentException("未知的爆炸形状 " + type + "，可选 " + String.join(" / ", TYPES));
        }
        if (Math.abs(offsetX) > 256 || Math.abs(offsetY) > 256 || Math.abs(offsetZ) > 256) {
            throw new IllegalArgumentException("offset 超出范围（每个轴 ±256）");
        }
        colors = colors == null ? List.of() : List.copyOf(colors);
        fade = fade == null ? List.of() : List.copyOf(fade);
        for (String color : colors) {
            requireColor(color);
        }
        for (String color : fade) {
            requireColor(color);
        }
    }

    private static void requireColor(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("颜色必须是 #RRGGBB 形式，当前 " + color);
        }
    }
}
