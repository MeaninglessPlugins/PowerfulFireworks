package org.eu.pcraft.powerfulfireworks.nms.common;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface NMSPlayer extends Wrapper<Player> {
    void sendFakeFirework(int id, UUID uuid, Location location, NMSEntityDataPacket packet);
    void sendEntityEvent(NMSEntityEventPacket packet);
    void sendRemoveEntity(int... ids);
}
