package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

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
        NMSEntityDataPacket fakeFirework = provider.createFireworkEntityDataPacket(entityId, FireworkUtil.getRandomFireworkItem());
        NMSEntityEventPacket eventPacket = provider.createEntityEvent(entityId, (byte) 17);
        NMSRemoveEntityPacket removePacket = provider.createRemoveEntityPacket(entityId);
        final Random rd = new Random();
        for(Player player:plugin.getServer().getOnlinePlayers()){
            if(!player.hasPermission(Permissions.SWITCHES_RANDOMFIREWORKS))
                continue;
            if(plugin.getMainConfig().randomFirework.blacklistWorlds.contains(player.getWorld().getName())){
                continue;
            }
            PowerfulFireworks.getInstance().nextTick(() -> {
                UUID uuid = UUID.randomUUID();
                provider.sendAddEntity(player, provider.createAddFireworkEntityPacket(entityId, uuid, FireworkUtil.getRandomLocation(player.getLocation(), FireworkUtil.getMaxDistance())), fakeFirework);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    provider.sendEntityEvent(player, eventPacket);
                    provider.sendRemoveEntity(player, removePacket);
                }, rd.nextInt(plugin.getMainConfig().randomFirework.flyTime.minimum, plugin.getMainConfig().randomFirework.flyTime.maximum));
            });
        }
        start();
    }
}