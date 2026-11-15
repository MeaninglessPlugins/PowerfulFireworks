package org.eu.pcraft.powerfulfireworks.utils.sender;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

import java.util.List;
import java.util.UUID;

public interface FireworkSender{
        NMSProvider nms = PowerfulFireworks.getInstance().getNms();
        void execute(int flyTime, ItemStack stack, Location location, List<Player> playerList);
        /** 按数组长度逐个分配实体 ID 与 UUID。 */
        static void calculateEntityIds(int[] ids, UUID[] uuids){
            if (ids.length != uuids.length) {
                throw new IllegalArgumentException(
                        "id/uuid array length mismatch: " + ids.length + " vs " + uuids.length);
            }
            for (int i = 0; i < ids.length; i++) {
                ids[i] = nms.allocateEntityId();
                uuids[i] = UUID.randomUUID();
            }
        }
    }