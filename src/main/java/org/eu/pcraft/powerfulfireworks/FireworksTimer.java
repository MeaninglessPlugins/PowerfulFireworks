package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Interval;
import org.eu.pcraft.powerfulfireworks.utils.LocationUtil;
import org.eu.pcraft.powerfulfireworks.utils.sender.TextFirework;
import org.eu.pcraft.powerfulfireworks.utils.sender.FireworkSender;
import org.eu.pcraft.powerfulfireworks.utils.sender.SingleFirework;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class FireworksTimer extends PepperRollTimer {
    public FireworksTimer(Interval<Integer> delay) {
        super(delay);
    }

    @Override
    protected void run() {
        FireworkSender sender;
        PepperConfigModule.RandomFirework config = plugin.getMainConfig().randomFirework;
        Random rd = new Random();
        int count = rd.nextInt(config.number.minimum,config.number.maximum);
        for(int idx = 0; idx<count; idx++)
            for(Player player : plugin.getServer().getOnlinePlayers()){
                int rdFlyTime = rd.nextInt(config.flyTime.minimum, config.flyTime.maximum);
                if(!player.hasPermission(Permissions.SWITCHES_RANDOMFIREWORKS))
                    continue;
                if(config.blacklistWorlds.contains(player.getWorld().getName())){
                    continue;
                }
                if(config.text.enabled && rd.nextDouble() <= config.text.chance){
                    BitmapFont font = plugin.getFonts().get(config.text.font);
                    sender = new TextFirework(font, config.text.texts.get(rd.nextInt(config.text.texts.size())), config.text.gap, config.text.size);
                } else {
                    sender = new SingleFirework();
                }
                sender.execute(rdFlyTime, FireworkUtil.getRandomFireworkItem(sender instanceof TextFirework),  LocationUtil.getRandomLocation(player.getLocation(), LocationUtil.getMaxDistance()), List.of(player));
            }
        start();
    }
}
