package org.eu.pcraft.powerfulfireworks.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSPlayer;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
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
                final NMSPlayer nms = provider.getPlayer(player);
                PowerfulFireworks.getInstance().nextTick(() -> {
                    UUID uuid = UUID.randomUUID();
                    nms.sendFakeFirework(114514, uuid, player.getLocation().add(0.0, 10.0, 0.0), provider.createFireworkEntityDataPacket(114514, item));
                    pl.nextTick(() -> {
                        nms.sendEntityEvent(provider.createEntityEvent(114514, (byte) 17)); // firework explosion
                        nms.sendRemoveEntity(114514);
                    });
                });
            } else {
                sender.sendMessage("Not firework rocket");
            }
        }

        return true;
    }
}
