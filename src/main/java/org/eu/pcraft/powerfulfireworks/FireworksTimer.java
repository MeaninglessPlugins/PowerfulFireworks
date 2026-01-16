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

    public FireworksTimer(Interval<Integer> delay) {
        super(delay);
    }

    @Override
    protected void run() {
        PepperConfigModule.RandomFirework config = plugin.getMainConfig().randomFirework;
        if (config == null) return;

        ThreadLocalRandom rd = ThreadLocalRandom.current();
        int count = rd.nextInt(config.number.minimum, config.number.maximum + 1);

        for (Player player : plugin.getPlayerSet()) {
            if (player == null || !player.isOnline()) continue;
            if (config.blacklistWorlds.contains(player.getWorld().getName())) continue;

            for (int idx = 0; idx < count; idx++) {
                int rdFlyTime = rd.nextInt(config.flyTime.minimum, config.flyTime.maximum + 1);

                FireworkSender sender;
                // 判断是否生成文字烟花
                if (config.text.enabled && rd.nextDouble() <= config.text.chance) {
                    BitmapFont font = plugin.getFonts().get(config.text.font);
                    String text = config.text.texts.get(rd.nextInt(config.text.texts.size()));
                    sender = new TextFirework(font, text, config.text.gap, config.text.size);
                } else {
                    sender = new SingleFirework();
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
}