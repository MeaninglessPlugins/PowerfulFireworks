package org.eu.pcraft.powerfulfireworks.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TestCommand extends Command {
    public TestCommand() {
        super("firework-test");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (sender instanceof Player player) {
            final ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.FIREWORK_ROCKET) {   // check rocket
                final PowerfulFireworks pl = PowerfulFireworks.getInstance();
                final NMSProvider provider = pl.getNms();
                final int id = provider.allocateEntityId();
                UUID uuid = UUID.randomUUID();
                final NMSAddEntityPacket add = provider.createAddFireworkEntityPacket(id, uuid, player.getLocation().add(0.0, 10.0, 0.0));
                final NMSEntityDataPacket data = provider.createFireworkEntityDataPacket(id, item);
                final NMSEntityEventPacket event = provider.createEntityEvent(id, (byte) 17);
                final NMSRemoveEntityPacket remove = provider.createRemoveEntityPacket(id);
                PowerfulFireworks.getInstance().nextTick(() -> {
                    provider.sendAddEntity(player, add, data);
                    pl.nextTick(() -> {
                        provider.sendEntityEvent(player, event);
                        provider.sendRemoveEntity(player, remove);
                    });
                });
            } else {
                sender.sendMessage("Not firework rocket");
            }
        }

        return true;
    }
}
