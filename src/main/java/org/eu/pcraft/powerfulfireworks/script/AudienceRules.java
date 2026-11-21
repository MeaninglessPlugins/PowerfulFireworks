package org.eu.pcraft.powerfulfireworks.script;

/**
 * 原版发包的取舍规则，只做算术，不碰 Bukkit，方便离线验证。
 *
 * <p>26.3 的两处来源：
 * <ul>
 *   <li>{@code ChunkMap.TrackedEntity.updatePlayer}：实体按 {@code clientTrackingRange × 16}
 *       与玩家视距取小，且只比水平距离（XZ）。烟花火箭的 tracking range 是 4，即 64 格。</li>
 *   <li>{@code ServerLevel.sendParticles}：粒子按玩家所在方块中心到发射点的三维距离，
 *       默认 32 格，{@code force} 时放宽到 512 格。</li>
 * </ul>
 */
public final class AudienceRules {

    /** 烟花火箭的实体跟踪范围：clientTrackingRange 4 × 16。 */
    public static final double FIREWORK_TRACKING = 64;

    /** 粒子默认发包范围。 */
    public static final double PARTICLE_RANGE = 32;

    /** force 时的粒子发包范围：32 × 16。 */
    public static final double PARTICLE_FORCED_RANGE = 512;

    private AudienceRules() {
    }

    /** 这个玩家对实体能看多远：自身视距与实体跟踪范围取小。 */
    public static double fireworkRange(int playerViewDistanceChunks) {
        return Math.min(FIREWORK_TRACKING, Math.max(0, playerViewDistanceChunks) * 16.0);
    }

    /** 实体包只看水平距离。 */
    public static boolean fireworkVisible(double dx, double dz, int playerViewDistanceChunks) {
        double range = fireworkRange(playerViewDistanceChunks);
        return dx * dx + dz * dz <= range * range;
    }

    /** 粒子包看三维距离，原点是玩家脚下方块的中心。 */
    public static boolean particleVisible(double dx, double dy, double dz, boolean force) {
        double range = force ? PARTICLE_FORCED_RANGE : PARTICLE_RANGE;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    /** 球形半径：脚本显式写的 {@code radius}。 */
    public static boolean sphereVisible(double dx, double dy, double dz, double radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
