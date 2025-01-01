package org.eu.pcraft.powerfulfireworks;

import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.UUID;

public class FireworksTimer extends PepperRollTimer{
    FireworkUtil fireworkUtil=new FireworkUtil();
    public FireworksTimer(int mindelay, int maxdelay, PowerfulFireworks javaPlugin) {
        super(mindelay, maxdelay, javaPlugin);
    }
    @Override
    protected void run(){
        final PowerfulFireworks pl = PowerfulFireworks.getInstance();
        final NMSProvider provider = pl.getNms();
        int entityId = provider.allocateEntityId();
        NMSEntityDataPacket fakeFirework = provider.createFireworkEntityDataPacket(entityId, fireworkUtil.getRandomFireworkItem());
        NMSEntityEventPacket eventPacket = provider.createEntityEvent(entityId, (byte) 17);
        NMSRemoveEntityPacket removePacket = provider.createRemoveEntityPacket(entityId);
        for(Player player:plugin.getServer().getOnlinePlayers()){
            PowerfulFireworks.getInstance().nextTick(() -> {
                UUID uuid = UUID.randomUUID();
                provider.sendAddEntity(player, provider.createAddFireworkEntityPacket(entityId, uuid, fireworkUtil.getRandomLocation(player)), fakeFirework);
                pl.nextTick(() -> {
                    provider.sendEntityEvent(player, eventPacket);
                    provider.sendRemoveEntity(player, removePacket);
                });
            });
        }
        start();
    }
}