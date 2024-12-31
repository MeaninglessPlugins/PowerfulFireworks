package org.eu.pcraft.powerfulfireworks.nms.common;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface NMSProvider {
    String getVersion();

    NMSPlayer getPlayer(Player player);

    NMSEntityEventPacket createEntityEvent(int entity, byte event);
    NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item);
}
