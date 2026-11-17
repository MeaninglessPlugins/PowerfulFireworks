package org.eu.pcraft.powerfulfireworks.config;

import org.bukkit.Bukkit;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigValues;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueCollector;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

/**
 * config.yml 的语义规则：只对照已知的运行边界做说明，不改值。
 *
 * <p>边界来源：火箭 56 tick 后升到 64 格外（客户端不再绘制本体）、生成半径受视距限制、
 * 每支火箭每接收者 5 个包、文本烟花的点数会被点数放大。规则只在这些边界被越过时提醒，
 * 内容正常的配置不会产生任何输出。</p>
 */
public final class ValidationRules {

    /** 超过这个 tick 数，火箭已爬出 64 格渲染范围。 */
    public static final int VISIBLE_TICKS = 56;

    /** 低于这个 tick 数，爆炸点贴着地面。 */
    private static final int MIN_FLY_TICKS = 10;

    /** 贴脸炸的阈值。 */
    private static final int MIN_DISTANCE = 24;

    /** distance 的出厂默认值。 */
    private static final int DEFAULT_DISTANCE = 20;

    /** 单轮数量的上限提示。 */
    private static final int MAX_NUMBER = 10;

    private ValidationRules() {
    }

    public static void validate(PepperConfigModule config, ConfigValues values, IssueCollector issues) {
        if (config == null || config.randomFirework == null) {
            return;
        }
        PepperConfigModule.RandomFirework random = config.randomFirework;

        interval(issues, "randomFirework.flyTime", random.flyTime, "升空时间");
        if (random.flyTime != null && random.flyTime.maximum > VISIBLE_TICKS) {
            warn(issues, "randomFirework.flyTime",
                    "最大值 " + random.flyTime.maximum + " tick 超出渲染范围：火箭在 56 tick 后已升到 64 格外，"
                            + "客户端不再绘制本体，引爆点看不到",
                    "建议最大值不超过 " + VISIBLE_TICKS);
        }
        if (random.flyTime != null && random.flyTime.minimum < MIN_FLY_TICKS) {
            warn(issues, "randomFirework.flyTime",
                    "最小值 " + random.flyTime.minimum + " tick 升空不足，爆炸点贴近地面，可能埋在方块里",
                    "建议最小值不小于 " + MIN_FLY_TICKS);
        }

        interval(issues, "randomFirework.delay", random.delay, "生成间隔");
        if (random.delay != null && random.delay.minimum <= 0) {
            warn(issues, "randomFirework.delay",
                    "间隔可以取到 0，意味着每 tick 都生成一轮，包量与粒子量都会成倍上涨",
                    "建议最小值不小于 5");
        }

        interval(issues, "randomFirework.number", random.number, "每轮数量");
        if (random.number != null && random.number.minimum <= 0) {
            warn(issues, "randomFirework.number",
                    "最小值 " + random.number.minimum + "：抽到 0 时这一轮不生成任何烟花",
                    "建议最小值不小于 1");
        }
        if (random.number != null && random.number.maximum > MAX_NUMBER) {
            warn(issues, "randomFirework.number",
                    "最大值 " + random.number.maximum + " 偏大：单轮每玩家最多 " + random.number.maximum
                            + " 支，叠加文本烟花后单 tick 的包量会明显上升",
                    "建议最大值不超过 " + MAX_NUMBER);
        }

        distance(issues, random, values);
        chance(issues, "randomFirework.launchChance", random.launchChance, "发射声");
        chance(issues, "randomFirework.explosionChance", random.explosionChance, "爆炸声");
        velocity(issues, "randomFirework.velocity", random.velocity);

        PepperConfigModule.RandomFireworkText text = random.text;
        if (text == null || !random.enabled || !text.enabled) {
            return;
        }
        if (text.font == null || text.font.isBlank()) {
            error(issues, "randomFirework.text.font", "字体 ID 为空，文本烟花无法生成", "填写 fonts 段里的一个键");
        }
        if (text.size <= 0) {
            error(issues, "randomFirework.text.size", "点阵间距必须大于 0，否则所有点会重合在同一处", "改成大于 0 的数值");
        }
        if (text.gap < 0) {
            error(issues, "randomFirework.text.gap", "字符间隔不能为负", "改成 0 或更大");
        }
        if (text.texts == null || text.texts.isEmpty()) {
            warn(issues, "randomFirework.text.texts", "已开启文本烟花，但候选文本为空，永远不会生成",
                    "至少写一条文本");
        } else if (text.chance <= 0) {
            warn(issues, "randomFirework.text.chance",
                    "概率为 " + text.chance + "，文本烟花永远不会出现", "想偶尔出现可以写 0.05");
        } else if (text.chance > 1) {
            error(issues, "randomFirework.text.chance",
                    "概率 " + text.chance + " 大于 1，每支随机烟花都会变成文本烟花，包量会被放到最大",
                    "改成 0 到 1 之间");
        }
        chance(issues, "randomFirework.text.launchChance", text.launchChance, "文本烟花的发射声");
        chance(issues, "randomFirework.text.explosionChance", text.explosionChance, "文本烟花的爆炸声");
        velocity(issues, "randomFirework.text.velocity", text.velocity);
    }

    private static void distance(IssueCollector issues, PepperConfigModule.RandomFirework random, ConfigValues values) {
        if (random.automaticDistance) {
            // 默认文件里就带着 distance，只有玩家把它改成非默认值时才说明"白写"
            boolean explicit = values != null && values.status("randomFirework.distance") == ConfigValues.Status.PRESENT;
            if (explicit && random.distance != DEFAULT_DISTANCE) {
                warn(issues, "randomFirework.distance",
                        "automaticDistance 为 true，这个值不会生效（半径按服务端视距计算）",
                        "要么关掉 automaticDistance，要么删掉这一行");
            }
            return;
        }
        if (random.distance < MIN_DISTANCE) {
            warn(issues, "randomFirework.distance",
                    "半径 " + random.distance + " 格过近，烟花会在玩家脸前炸开", "建议不小于 " + MIN_DISTANCE);
        }
        int maxVisible = viewDistance() * 16;
        if (random.distance > maxVisible) {
            warn(issues, "randomFirework.distance",
                    "半径 " + random.distance + " 格超出视距（" + maxVisible + " 格），远处的烟花玩家看不到",
                    "建议不超过 " + maxVisible);
        }
    }

    private static void interval(IssueCollector issues, String path, Interval<Integer> value, String what) {
        if (value == null || value.minimum == null || value.maximum == null) {
            error(issues, path, what + "没有解析出来", "写成单个数字、[最小值, 最大值] 或 {minimum, maximum}");
            return;
        }
        if (value.minimum > value.maximum) {
            error(issues, path, what + "的最小值 " + value.minimum + " 大于最大值 " + value.maximum,
                    "改成 [最小值, 最大值]");
        }
    }

    private static void velocity(IssueCollector issues, String path, double value) {
        if (value < 0) {
            warn(issues, path, "初速度 " + value + " 为负，火箭会先下沉再上升",
                    "想要更慢的升空就把 flyTime 调小，或者保持 0");
        } else if (value > 2) {
            warn(issues, path, "初速度 " + value + " 格/tick 偏大，火箭会很快飞出 64 格的渲染范围",
                    "一般 0 到 1 之间就够用");
        }
    }

    private static void chance(IssueCollector issues, String path, double value, String what) {
        if (value < 0 || value > 1) {
            String effect = value > 1 ? "始终不播放" : "始终播放";
            error(issues, path, what + "的播放概率 " + value + " 不在 0..1，实际表现为" + effect,
                    "改成 0 到 1 之间");
        }
    }

    private static int viewDistance() {
        try {
            return Math.max(2, Bukkit.getViewDistance());
        } catch (Throwable ignored) {
            return 10;
        }
    }

    private static void warn(IssueCollector issues, String path, String message, String hint) {
        issues.add(new ConfigIssue(IssueLevel.WARN, path, message, hint));
    }

    private static void error(IssueCollector issues, String path, String message, String hint) {
        issues.add(new ConfigIssue(IssueLevel.ERROR, path, message, hint));
    }
}
