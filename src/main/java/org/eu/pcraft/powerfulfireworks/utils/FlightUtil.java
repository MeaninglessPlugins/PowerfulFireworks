package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.Collection;

/**
 * 烟花假实体的飞行范围计算。
 *
 * <p>客户端只渲染 64 格以内的 {@code FireworkRocketEntity}
 * （{@code shouldRenderAtSqrDistance: distance < 4096.0}），服务端无法关闭。
 * 假实体在客户端由本地 tick 推进：add 包速度为零，每 tick 只有 +0.04 的竖直速度、水平恒为 0，因此
 *
 * <pre>v(t) = 0.04 * t,  y(t) = 0.02 * t * (t + 1)</pre>
 *
 * <p>据此可以反解出 {@code flyTime} 的上限，让火箭在"最后一 tick 仍可见"的位置爆炸。
 */
public final class FlightUtil {

    /** 客户端渲染假实体的硬上限（格）。 */
    public static final double RENDER_DISTANCE = 64.0;

    /** 预留余量，避免玩家移动或镜头偏移导致爆炸瞬间刚好越界。 */
    private static final double SAFE_MARGIN = 8.0;

    private FlightUtil() {
    }

    /**
     * 第 {@code ticks} tick 时，火箭相对生成点的上升高度（格）。
     */
    public static double riseAt(int ticks) {
        if (ticks <= 0) return 0.0;
        return 0.02 * ticks * (ticks + 1.0);
    }

    /**
     * 上升到 {@code rise} 格所需的 tick 数，即 {@link #riseAt(int)} 的反函数。
     * 解 {@code 0.02t² + 0.02t - rise = 0}。
     */
    public static int ticksForRise(double rise) {
        if (rise <= 0.0) return 0;
        return (int) Math.ceil((-0.02 + Math.sqrt(0.0004 + 0.08 * rise)) / 0.04);
    }

    /**
     * 把飞行时长裁剪到观看者仍能看见的最大 tick 数。
     *
     * <p>只考虑与生成点同世界的在线玩家；水平距离已经超出可见半径的玩家不作为约束
     * （这枚烟花对他们本来就不可能出现）。
     *
     * @param flyTime 期望的飞行 tick 数
     * @param spawn   烟花生成点
     * @param viewers 能看到该烟花的玩家
     * @return 裁剪后的 tick 数；无可裁剪时原样返回
     */
    public static int clampFlyTime(int flyTime, Location spawn, Collection<? extends Player> viewers) {
        if (flyTime <= 1 || spawn == null || spawn.getWorld() == null
                || viewers == null || viewers.isEmpty()) {
            return flyTime;
        }

        final double safe = RENDER_DISTANCE - SAFE_MARGIN;
        final double safeSq = safe * safe;

        int limit = flyTime;
        boolean constrained = false;

        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline()) continue;
            if (!spawn.getWorld().equals(viewer.getWorld())) continue;

            Location eye = viewer.getEyeLocation();
            double dx = spawn.getX() - eye.getX();
            double dz = spawn.getZ() - eye.getZ();
            double horizontalSq = dx * dx + dz * dz;

            // 生成点的水平距离就已经超出可见半径，这枚烟花对该玩家不可见，不作为约束
            if (horizontalSq >= safeSq) continue;

            // 该玩家能看到的最高高度：眼睛高度 + 可见球在该水平距离上的竖直分量
            double topY = eye.getY() + Math.sqrt(safeSq - horizontalSq);
            int maxTicks = ticksForRise(topY - spawn.getY());

            if (maxTicks < limit) {
                limit = maxTicks;
                constrained = true;
            }
        }

        if (!constrained) return flyTime;

        int result = Math.max(1, limit);
        if (result != flyTime) {
            PowerfulFireworks plugin = PowerfulFireworks.getInstance();
            if (plugin != null && plugin.getMainConfig() != null && plugin.getMainConfig().debug) {
                plugin.getSLF4JLogger().info(
                        "[flight] flyTime {} -> {} (render limit {} blocks)",
                        flyTime, result, (int) safe);
            }
        }
        return result;
    }
}
