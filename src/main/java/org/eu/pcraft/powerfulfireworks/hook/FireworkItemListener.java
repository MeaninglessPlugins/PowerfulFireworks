package org.eu.pcraft.powerfulfireworks.hook;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkStartupConfig;

import java.util.Arrays;
import java.util.function.Function;

public class FireworkItemListener implements Listener {
    @EventHandler
    public void onFireworkExplode(FireworkExplodeEvent event) {
        Firework entity = event.getEntity();
        ItemStack item = entity.getItem();
        String id = NBT.get(item, (Function<ReadableItemNBT, String>) r -> r.getString(PowerfulFireworks.ITEM_KEY));
        if (id != null) {
            FireworkScheduler sched = PowerfulFireworks.getInstance().getSchedulers().get(id);
            if (sched != null && sched.isAllowActivationByItems()) {
                event.setCancelled(true);
                sched.execute(new FireworkStartupConfig(
                        entity.getLocation(),
                        Arrays.asList(entity.getWorld().getPlayers().toArray(new Player[0]))
                ));
            }
        }
    }
}
