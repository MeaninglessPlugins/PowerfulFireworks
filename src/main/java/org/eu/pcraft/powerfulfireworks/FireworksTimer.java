package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Interval;
import org.eu.pcraft.powerfulfireworks.utils.LocationUtil;
import org.eu.pcraft.powerfulfireworks.utils.sender.TextFirework;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class FireworksTimer extends PepperRollTimer {
    public FireworksTimer(Interval<Integer> delay) {
        super(delay);
    }

    @Override
    protected void run() {
        final NMSProvider provider = plugin.getNms();
        int entityId = provider.allocateEntityId();
        NMSEntityDataPacket fakeFirework = provider.createFireworkEntityDataPacket(entityId, FireworkUtil.getRandomFireworkItem(false));
        NMSEntityEventPacket eventPacket = provider.createEntityEvent(entityId, (byte) 17);
        NMSRemoveEntityPacket removePacket = provider.createRemoveEntityPacket(entityId);
        final Random rd = new Random();
        for(Player player:plugin.getServer().getOnlinePlayers()){
            int rdFlyTime = rd.nextInt(plugin.getMainConfig().randomFirework.flyTime.minimum, plugin.getMainConfig().randomFirework.flyTime.maximum);
            if(!player.hasPermission(Permissions.SWITCHES_RANDOMFIREWORKS))
                continue;
            if(plugin.getMainConfig().randomFirework.blacklistWorlds.contains(player.getWorld().getName())){
                continue;
            }
            if(plugin.getMainConfig().randomFirework.text.enabled && rd.nextDouble() <= plugin.getMainConfig().randomFirework.text.chance){
                BitmapFont font = plugin.getFonts().get(plugin.getMainConfig().randomFirework.text.font);
                TextFirework firework = new TextFirework(font, plugin.getMainConfig().randomFirework.text.texts.get(rd.nextInt(plugin.getMainConfig().randomFirework.text.texts.size())), plugin.getMainConfig().randomFirework.text.gap, plugin.getMainConfig().randomFirework.text.size);
                firework.execute(rdFlyTime, FireworkUtil.getRandomFireworkItem(true),  LocationUtil.getRandomLocation(player.getLocation(), LocationUtil.getMaxDistance()), List.of(player));
                continue;
            }
            PowerfulFireworks.getInstance().nextTick(() -> {
                UUID uuid = UUID.randomUUID();
                provider.sendAddEntity(player, provider.createAddFireworkEntityPacket(entityId, uuid, LocationUtil.getRandomLocation(player.getLocation(), LocationUtil.getMaxDistance())), fakeFirework);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    provider.sendEntityEvent(player, eventPacket);
                    provider.sendRemoveEntity(player, removePacket);
                }, rdFlyTime);
            });
        }
        start();
    }
}