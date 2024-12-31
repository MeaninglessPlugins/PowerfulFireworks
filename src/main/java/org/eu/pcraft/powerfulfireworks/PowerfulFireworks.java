package org.eu.pcraft.powerfulfireworks;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class PowerfulFireworks extends JavaPlugin {

    @Override
    public void onEnable() {
        ////bStats////
        int pluginId = 21763;
        Metrics metrics = new Metrics(this, pluginId);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
