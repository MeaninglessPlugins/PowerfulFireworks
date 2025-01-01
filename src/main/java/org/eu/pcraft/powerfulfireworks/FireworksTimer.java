package org.eu.pcraft.powerfulfireworks;

import org.bukkit.FireworkEffect;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
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
        for(Player player:plugin.getServer().getOnlinePlayers()){
            final PowerfulFireworks pl = PowerfulFireworks.getInstance();
            final NMSProvider provider = pl.getNms();
            final NMSPlayer nms = provider.getPlayer(player);
            PowerfulFireworks.getInstance().nextTick(() -> {
                UUID uuid = UUID.randomUUID();
                nms.sendFakeFirework(114514, uuid, player.getLocation().add(0.0, 5.0, 0.0), provider.createFireworkEntityDataPacket(114514, fireworkUtil.getRandomFireworkItem()));
                pl.nextTick(() -> {
                    nms.sendEntityEvent(provider.createEntityEvent(114514, (byte) 17)); // firework explosion
                    nms.sendRemoveEntity(114514);
                });
            });
        }
        start();
    }
}