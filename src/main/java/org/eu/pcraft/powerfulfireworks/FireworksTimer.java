package org.eu.pcraft.powerfulfireworks;

import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.utils.*;
import org.eu.pcraft.powerfulfireworks.utils.sender.FireworkSender;
import org.eu.pcraft.powerfulfireworks.utils.sender.SingleFirework;
import org.eu.pcraft.powerfulfireworks.utils.sender.TextFirework;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FireworksTimer extends PepperRollTimer {

    /** 字体缺失只提醒一次，避免每轮刷屏 */
    private boolean warnedMissingFont;

    public FireworksTimer(Interval<Integer> delay) {
        super(delay);
    }

    @Override
    protected void run() {
        PepperConfigModule.RandomFirework config = plugin.getMainConfig().randomFirework;
        if (config == null) return;

        ThreadLocalRandom rd = ThreadLocalRandom.current();
        int count = roll(config.number.minimum, config.number.maximum);

        for (Player player : plugin.getPlayerSet()) {
            if (player == null || !player.isOnline()) continue;
            if (config.blacklistWorlds.contains(player.getWorld().getName())) continue;

            for (int idx = 0; idx < count; idx++) {
                int rdFlyTime = roll(config.flyTime.minimum, config.flyTime.maximum);

                FireworkSender sender = null;
                // 判断是否生成文字烟花
                if (config.text.enabled && rd.nextDouble() <= config.text.chance) {
                    BitmapFont font = plugin.getFonts().get(config.text.font);
                    if (font == null || config.text.texts.isEmpty()) {
                        // 字体没加载成功（文件缺失或改名）或者没配文字，退回普通烟花
                        if (!warnedMissingFont) {
                            warnedMissingFont = true;
                            plugin.getSLF4JLogger().warn(
                                    "config.yml 里的 randomFirework.text 配置不可用（字体 {}，文字 {} 句），文本烟花将被跳过",
                                    config.text.font, config.text.texts.size());
                        }
                    } else {
                        String text = config.text.texts.get(rd.nextInt(config.text.texts.size()));
                        sender = new TextFirework(font, text, config.text.gap, config.text.size,
                                config.text.launchChance, config.text.explosionChance);
                    }
                }
                if (sender == null) {
                    sender = new SingleFirework(config.launchChance, config.explosionChance);
                }

                sender.execute(
                        rdFlyTime,
                        FireworkUtil.getRandomFireworkItem(sender instanceof TextFirework),
                        LocationUtil.getRandomLocation(player.getLocation(), LocationUtil.getMaxDistance()),
                        List.of(player)
                );
            }
        }
    }

    /** 在区间内随机取一个整数，两端写反或相等时也不会抛异常。 */
    private static int roll(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}