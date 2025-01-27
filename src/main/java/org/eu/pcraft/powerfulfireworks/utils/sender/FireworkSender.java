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
        static void calculateEntityIds(int entityNum, int[] id, UUID[] uuid){
            for (int i = 0; i < entityNum + 1; i++) {
                id[i] = nms.allocateEntityId();
                uuid[i] = UUID.randomUUID();
            }
        }
    }