package org.eu.pcraft.powerfulfireworks.script;

/**
 * 一次粒子发射请求。
 *
 * <p>与 {@link FireworkRequest} 同一层：脚本只描述要放什么，宿主负责选 API 与发包。
 * 构造时校验，宿主可以直接信任这些值。
 *
 * @param particle 粒子名，对应 Bukkit 的 {@code Particle} 常量名（大小写不敏感）
 * @param count    粒子数量；1 表示精确点位，大于 1 时在 spread 盒子里随机撒
 * @param spreadX  随机散布半径（格）；0 表示都落在同一点
 * @param spreadY  随机散布高度
 * @param spreadZ  随机散布半径
 * @param speed    速度倍率，即 Bukkit API 的 {@code extra}
 * @param size     粒子尺寸，只对带颜色数据的粒子有效
 * @param color    起始颜色 {@code #RRGGBB}，只对带颜色数据的粒子有效
 * @param fade     过渡颜色，只对颜色过渡类粒子（dust_color_transition）有效
 * @param offsetX  相对原点的水平偏移
 * @param offsetY  相对原点的垂直偏移
 * @param offsetZ  相对原点的水平偏移
 * @param force    是否强制发给接收者：跳过客户端的距离与性能设置检查
 */
public record ParticleRequest(
        String particle,
        int count,
        double spreadX,
        double spreadY,
        double spreadZ,
        double speed,
        double size,
        String color,
        String fade,
        double offsetX,
        double offsetY,
        double offsetZ,
        boolean force) implements Emission {

    public ParticleRequest {
        if (particle == null || particle.isBlank()) {
            throw new IllegalArgumentException("particle 不能为空");
        }
        if (count < 1 || count > 4096) {
            throw new IllegalArgumentException("count 必须在 1..4096 之间，当前 " + count);
        }
        requireSpread(spreadX, "spreadX");
        requireSpread(spreadY, "spreadY");
        requireSpread(spreadZ, "spreadZ");
        if (speed < 0 || speed > 16) {
            throw new IllegalArgumentException("speed 必须在 0..16 之间，当前 " + speed);
        }
        if (size < 0.1 || size > 8) {
            throw new IllegalArgumentException("size 必须在 0.1..8 之间，当前 " + size);
        }
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("color 必须是 #RRGGBB 形式，当前 " + color);
        }
        if (fade != null && !fade.isEmpty() && !fade.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("fade 必须是 #RRGGBB 形式，当前 " + fade);
        }
        if (Math.abs(offsetX) > 256 || Math.abs(offsetY) > 256 || Math.abs(offsetZ) > 256) {
            throw new IllegalArgumentException("offset 超出范围（每个轴 ±256）");
        }
    }

    private static void requireSpread(double value, String name) {
        if (value < 0 || value > 32) {
            throw new IllegalArgumentException(name + " 必须在 0..32 之间，当前 " + value);
        }
    }
}
