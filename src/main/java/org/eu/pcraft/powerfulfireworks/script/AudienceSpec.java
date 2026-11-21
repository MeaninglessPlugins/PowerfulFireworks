package org.eu.pcraft.powerfulfireworks.script;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次发射"发给谁"的规则。
 *
 * <p>它只描述规则，不带任何玩家对象：真正的名单在发射那一刻由宿主解析，
 * 所以进服、走开、改视距都会自然生效。
 */
public record AudienceSpec(
        Kind kind,
        double radius,
        Double centerX,
        Double centerY,
        Double centerZ,
        String world,
        List<String> names) {

    /** 最大半径：再大就不如直接全服，也避免手滑写成天文数字。 */
    public static final double MAX_RADIUS = 512;
    /** 观众中心相对原点的最大偏移。 */
    public static final double MAX_CENTER_OFFSET = 2048;
    public static final int MAX_NAMES = 256;

    public enum Kind {
        /** 按原版规则：烟花看实体跟踪范围，粒子看 32/512 格。 */
        DEFAULT,
        RADIUS,
        /** 某个世界里的所有玩家；{@code all()} 就是"发射点所在世界"。 */
        WORLD,
        PLAYERS,
        NONE
    }

    public AudienceSpec {
        List<String> cleaned = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                String trimmed = name == null ? "" : name.trim();
                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException("观众名单里有空名字");
                }
                cleaned.add(trimmed);
            }
        }
        names = List.copyOf(cleaned);
        String trimmedWorld = world == null ? null : world.trim();
        if (trimmedWorld != null && trimmedWorld.isEmpty()) {
            throw new IllegalArgumentException("世界名不能为空");
        }
        world = trimmedWorld;
        if (kind == Kind.RADIUS && !(radius > 0 && radius <= MAX_RADIUS)) {
            throw new IllegalArgumentException("观众半径必须在 0 到 " + (int) MAX_RADIUS + " 之间，当前 " + radius);
        }
        for (Double value : new Double[]{centerX, centerY, centerZ}) {
            if (value != null && Math.abs(value) > MAX_CENTER_OFFSET) {
                throw new IllegalArgumentException("观众中心相对原点不能超过 ±" + (int) MAX_CENTER_OFFSET + "：" + value);
            }
        }
        if (kind == Kind.PLAYERS) {
            if (names.isEmpty()) {
                throw new IllegalArgumentException("观众名单不能为空，至少写一个玩家名");
            }
            if (names.size() > MAX_NAMES) {
                throw new IllegalArgumentException("观众名单最多 " + MAX_NAMES + " 个名字，当前 " + names.size());
            }
        }
    }

    /** 不传观众时的默认值：交给宿主按原版规则判断。 */
    public static AudienceSpec vanilla() {
        return new AudienceSpec(Kind.DEFAULT, 0, null, null, null, null, List.of());
    }

    public static AudienceSpec world(String name) {
        return new AudienceSpec(Kind.WORLD, 0, null, null, null, name, List.of());
    }

    public static AudienceSpec players(List<String> names) {
        return new AudienceSpec(Kind.PLAYERS, 0, null, null, null, null, names);
    }

    public static AudienceSpec none() {
        return new AudienceSpec(Kind.NONE, 0, null, null, null, null, List.of());
    }

    /** 有没有显式指定中心；没有就用演出原点。 */
    public boolean hasCenter() {
        return centerX != null;
    }

    public double centerX(double fallback) {
        return centerX == null ? fallback : centerX;
    }

    public double centerY(double fallback) {
        return centerY == null ? fallback : centerY;
    }

    public double centerZ(double fallback) {
        return centerZ == null ? fallback : centerZ;
    }
}
