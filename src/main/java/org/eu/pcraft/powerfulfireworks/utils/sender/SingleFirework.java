package org.eu.pcraft.powerfulfireworks.utils.sender;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.List;

@Getter
    public class SingleFirework implements FireworkSender{

        @Override
        public void execute(int flyTime, ItemStack stack, Location fwLoc, List<Player> playerList) {
            int[] id = {FireworkUtil.broadcastFireworkCreate(
                        playerList,
                        stack,
                        fwLoc)};
            BukkitRunnable fireworkExplosionTask = new BukkitRunnable() {
                @Override
                public void run() {
                    FireworkUtil.broadcastFireworkExplosion(playerList, id);
                }
            };
            fireworkExplosionTask.runTaskLater(PowerfulFireworks.getInstance(), flyTime);
        }
    }
