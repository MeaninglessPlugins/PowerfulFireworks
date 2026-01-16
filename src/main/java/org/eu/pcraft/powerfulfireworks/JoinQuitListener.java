package org.eu.pcraft.powerfulfireworks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {
    @EventHandler
    void onJoin(PlayerJoinEvent event){
        PowerfulFireworks plugin = PowerfulFireworks.getInstance();
        if(!plugin.getMainConfig().randomFirework.enabled){
            return;
        }
        plugin.updateToggle(event.getPlayer());
    }
    @EventHandler
    void onQuit(PlayerQuitEvent event){
        PowerfulFireworks plugin = PowerfulFireworks.getInstance();
        if(!plugin.getMainConfig().randomFirework.enabled){
            return;
        }
        plugin.updateToggle(event.getPlayer());
    }
}
