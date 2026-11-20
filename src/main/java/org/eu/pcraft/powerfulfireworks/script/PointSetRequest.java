package org.eu.pcraft.powerfulfireworks.script;

import java.util.ArrayList;
import java.util.List;

/**
 * 一组坐标 + 一种发射物。
 *
 * <p>坐标相对演出原点，单位是格；发射参数与单次发射完全一样，
 * 所以同一组坐标换成烟花或粒子只需要换 {@link Emission}。
 *
 * @param emission 每个点上放什么
 * @param points   坐标，每个元素是 {@code [x, y, z]}
 */
public record PointSetRequest(Emission emission, List<double[]> points) {

    /** 一次点集的坐标上限：挡住手写的超大数组，也给客户端留余量。 */
    public static final int MAX_POINTS = 1024;

    /**
     * 点集里一次最多放多少支烟花。
     *
     * <p>粒子一个包就发完了，烟花是每支五六个包加一个实体，挨个点在点上炸的话
     * 很容易把服务器按在地上，所以这里给个上限，超了直接报错而不是慢慢卡死。
     */
    public static final int MAX_FIREWORKS = 512;

    private static final double MAX_COORDINATE = 256;

    public PointSetRequest {
        if (emission == null) {
            throw new IllegalArgumentException("点集需要一个发射参数");
        }
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("坐标不能为空");
        }
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("坐标最多 " + MAX_POINTS + " 个，当前 " + points.size());
        }
        List<double[]> copy = new ArrayList<>(points.size());
        for (double[] point : points) {
            if (point == null || point.length != 3) {
                throw new IllegalArgumentException("每个坐标必须是 [x, y, z]");
            }
            for (double value : point) {
                if (Math.abs(value) > MAX_COORDINATE) {
                    throw new IllegalArgumentException("坐标超出范围（每个轴 ±" + (int) MAX_COORDINATE + "）");
                }
            }
            copy.add(new double[]{point[0], point[1], point[2]});
        }
        if (emission instanceof FireworkRequest firework && (long) points.size() * firework.count() > MAX_FIREWORKS) {
            throw new IllegalArgumentException("点集里最多放 " + MAX_FIREWORKS + " 支烟花，当前 "
                    + points.size() + " 个坐标 × " + firework.count() + " 支 = "
                    + (long) points.size() * firework.count());
        }
        points = List.copyOf(copy);
    }
}
