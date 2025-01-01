package org.eu.pcraft.powerfulfireworks;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.eu.pcraft.powerfulfireworks.commands.TestCommand;
import org.eu.pcraft.powerfulfireworks.config.ConfigManager;
import org.eu.pcraft.powerfulfireworks.nms.NMSSelector;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

import java.nio.file.Path;

public final class PowerfulFireworks extends JavaPlugin {
    @Getter
    private static PowerfulFireworks instance;

    @Getter
    private NMSProvider nms;

    ConfigManager configManager;

    @Override
    public void onLoad() {
        //instance
        PowerfulFireworks.instance = this;

        //config
        configManager=new ConfigManager(Path.of(getDataFolder() + "/config.yml"), instance);
        configManager.loadConfig();
        if(configManager.configModule.debug){
            getLogger().warning("***WARNING***");
            getLogger().warning("You are using the DEBUGING mode!");
            getLogger().warning("To make it disabled, change 'debug' in config.yml into false!");
        }

        //nms
        this.nms = NMSSelector.getImplementation(Bukkit.getMinecraftVersion());
        if (this.nms == null) {
            throw new UnsupportedOperationException("Unsupported version " + Bukkit.getMinecraftVersion());
        } else {
            getSLF4JLogger().info("Using NMS version {}", this.nms.getVersion());
        }
    }

    @Override
    public void onEnable() {
        if (this.nms == null)
            throw new IllegalStateException("NMS not initialized");
        //bStats
        int pluginId = 24294;
        Metrics metrics = new Metrics(this, pluginId);
        //Permissions
        Permissions.register();

        // Test command
        Bukkit.getCommandMap().register("fireworks", new TestCommand());
        //Listener
        Bukkit.getPluginManager().registerEvents(new EventListener(), instance);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void runAfter(int ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(this, runnable, ticks);
    }

    public void nextTick(Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }
}
