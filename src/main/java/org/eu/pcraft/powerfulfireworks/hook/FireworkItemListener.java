package org.eu.pcraft.powerfulfireworks.hook;

import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkStartupConfig;

import java.util.Arrays;

public class FireworkItemListener implements Listener {
    @EventHandler
    public void onFireworkExplode(FireworkExplodeEvent event) {
        Firework entity = event.getEntity();
        ItemStack item = entity.getItem();
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String id = container.get(PowerfulFireworks.ITEM_KEY, PersistentDataType.STRING);
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
