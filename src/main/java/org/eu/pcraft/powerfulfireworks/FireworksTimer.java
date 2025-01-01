package org.eu.pcraft.powerfulfireworks;

import org.bukkit.FireworkEffect;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSPlayer;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
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
        NMSEntityDataPacket fakeFirework = provider.createFireworkEntityDataPacket(114514, fireworkUtil.getRandomFireworkItem());
        NMSEntityEventPacket eventPacket=provider.createEntityEvent(114514, (byte) 17);
        for(Player player:plugin.getServer().getOnlinePlayers()){
            final NMSPlayer nms = provider.getPlayer(player);
            PowerfulFireworks.getInstance().nextTick(() -> {
                UUID uuid = UUID.randomUUID();
                nms.sendFakeFirework(114514, uuid, fireworkUtil.getRandomLocation(player), fakeFirework);
                pl.nextTick(() -> {
                    nms.sendEntityEvent(eventPacket); // firework explosion
                    nms.sendRemoveEntity(114514);
                });
            });
        }
        start();
    }
}