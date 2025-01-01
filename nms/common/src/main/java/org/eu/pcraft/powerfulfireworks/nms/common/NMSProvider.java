package org.eu.pcraft.powerfulfireworks.nms.common;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface NMSProvider {
    String getVersion();

    NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location);
    NMSEntityEventPacket createEntityEvent(int entity, byte event);
    NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item);
    NMSRemoveEntityPacket createRemoveEntityPacket(int... id);

    void sendAddEntity(Player player, NMSAddEntityPacket addEntityPacket, NMSEntityDataPacket dataPacket);
    void sendEntityEvent(Player player, NMSEntityEventPacket packet);
    void sendRemoveEntity(Player player, NMSRemoveEntityPacket removeEntityPacket);

    int allocateEntityId();
}
