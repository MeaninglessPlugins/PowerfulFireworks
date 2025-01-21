package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.Random;
import java.util.UUID;

public class FireworksTimer extends PepperRollTimer {
    public FireworksTimer(int mindelay, int maxdelay, PowerfulFireworks javaPlugin) {
        super(mindelay, maxdelay, javaPlugin);
    }

    @Override
    protected void run() {
        final PowerfulFireworks pl = PowerfulFireworks.getInstance();
        final NMSProvider provider = pl.getNms();
        int entityId = provider.allocateEntityId();
        NMSEntityDataPacket fakeFirework = provider.createFireworkEntityDataPacket(entityId, FireworkUtil.getRandomFireworkItem());
        NMSEntityEventPacket eventPacket = provider.createEntityEvent(entityId, (byte) 17);
        NMSRemoveEntityPacket removePacket = provider.createRemoveEntityPacket(entityId);
        final Random rd = new Random();
        for(Player player:plugin.getServer().getOnlinePlayers()){
            if(!player.hasPermission(Permissions.TOGGLE_RANDOMFIREWORKS))
                continue;
            PowerfulFireworks.getInstance().nextTick(() -> {
                UUID uuid = UUID.randomUUID();
                provider.sendAddEntity(player, provider.createAddFireworkEntityPacket(entityId, uuid, FireworkUtil.getRandomLocation(player.getLocation(), FireworkUtil.getMaxDistance())), fakeFirework);
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    provider.sendEntityEvent(player, eventPacket);
                    provider.sendRemoveEntity(player, removePacket);
                }, rd.nextInt(pl.getMainConfig().randomFirework.min_fly_time, pl.getMainConfig().randomFirework.max_fly_time));
            });
        }
        start();
    }
}