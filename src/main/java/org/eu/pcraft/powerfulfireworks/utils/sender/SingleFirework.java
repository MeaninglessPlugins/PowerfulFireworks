package org.eu.pcraft.powerfulfireworks.utils.sender;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Getter
    public class SingleFirework implements FireworkSender{
        ItemStack stack;
        List<Player> playerList;

        public SingleFirework(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public void execute(int flyTime, ItemStack stack, Location location, List<Player> playerList) {

        }
    }