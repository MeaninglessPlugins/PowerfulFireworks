package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
        Random rd = new Random();
        int count = rd.nextInt(plugin.getMainConfig().randomFirework.number.minimum,plugin.getMainConfig().randomFirework.number.maximum);
        for(int idx = 0; idx<count; idx++)
        for(Player player : plugin.getServer().getOnlinePlayers()){
            int rdFlyTime = rd.nextInt(plugin.getMainConfig().randomFirework.flyTime.minimum, plugin.getMainConfig().randomFirework.flyTime.maximum);
            if(!player.hasPermission(Permissions.SWITCHES_RANDOMFIREWORKS))
                continue;
            if(plugin.getMainConfig().randomFirework.blacklistWorlds.contains(player.getWorld().getName())){
                continue;
            }
            if(plugin.getMainConfig().randomFirework.text.enabled && rd.nextDouble() <= plugin.getMainConfig().randomFirework.text.chance){
                BitmapFont font = plugin.getFonts().get(plugin.getMainConfig().randomFirework.text.font);
                sender = new TextFirework(font, plugin.getMainConfig().randomFirework.text.texts.get(rd.nextInt(plugin.getMainConfig().randomFirework.text.texts.size())), plugin.getMainConfig().randomFirework.text.gap, plugin.getMainConfig().randomFirework.text.size);
            } else {
                sender = new SingleFirework();
            }
            sender.execute(rdFlyTime, FireworkUtil.getRandomFireworkItem(sender instanceof TextFirework),  LocationUtil.getRandomLocation(player.getLocation(), LocationUtil.getMaxDistance()), List.of(player));
        }
        start();
    }
}
