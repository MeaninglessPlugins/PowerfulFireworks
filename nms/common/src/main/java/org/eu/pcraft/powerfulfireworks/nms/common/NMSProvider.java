package org.eu.pcraft.powerfulfireworks.nms.common;

import org.bukkit.entity.Player;

public interface NMSProvider {
    String getVersion();

    NMSPlayer getPlayer(Player player);

    NMSEntityEventPacket createEntityEvent(int entity, byte event);
}
