package org.eu.pcraft.powerfulfireworks;

import cn.afternode.commons.bukkit.BukkitPluginContext;
import cn.afternode.commons.bukkit.ConfigurationLocalizations;
import cn.afternode.commons.bukkit.IAdventureLocalizations;
import cn.afternode.commons.bukkit.message.MessageBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.eu.pcraft.powerfulfireworks.commands.MainCommand;
import org.eu.pcraft.powerfulfireworks.commands.TestCommand;
import org.eu.pcraft.powerfulfireworks.config.ConfigManager;
import org.eu.pcraft.powerfulfireworks.config.MessagesConfigModule;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.nms.NMSSelector;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

import java.io.File;
import java.nio.file.Path;

public final class PowerfulFireworks extends JavaPlugin {
    @Getter
    private static PowerfulFireworks instance;

    @Getter
    private BukkitPluginContext context;
    @Getter
    private NMSProvider nms;

    @Getter
    private ConfigManager configManager;
    @Getter
    private ConfigManager messagesManager;

    @Getter
    private MessagesConfigModule messageConfig = new MessagesConfigModule();
    @Getter
    public PepperConfigModule mainConfig = new PepperConfigModule();
    FireworksTimer timer;

    @Override
    public void onLoad() {
        //instance
        PowerfulFireworks.instance = this;
        this.context = new BukkitPluginContext(this);

        //config
        loadConfigurations();
        if(mainConfig.debug){
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

        // commands
        CommandMap map = Bukkit.getCommandMap();
        map.register("fireworks", new TestCommand());
        map.register("fireworks", new MainCommand());

        //Listener
        Bukkit.getPluginManager().registerEvents(new EventListener(), instance);
        //Timer
        timer=new FireworksTimer(
                mainConfig.randomFirework.min_delay,
                mainConfig.randomFirework.max_delay, instance);
        timer.start();
    }

    @Override
    public void onDisable() {
        timer.stop();
        // Plugin shutdown logic
    }

    public void runAfter(int ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(this, runnable, ticks);
    }

    public void nextTick(Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }

    public void loadConfigurations() {
        //load
        configManager=new ConfigManager(Path.of(getDataFolder() + "/config.yml"), mainConfig);
        messagesManager=new ConfigManager(Path.of(getDataFolder() + "/messages.yml"), messageConfig);
        configManager.loadConfig();
        messagesManager.loadConfig();
        //message
        try {
            this.context.setLocalizations(new ConfigurationLocalizations(this.context.loadConfiguration("messages.yml")));
            this.context.setDefaultLocalizeMode(IAdventureLocalizations.LocalizeMode.MM);
            this.context.setMessageLinePrefix(new MessageBuilder()
                    .localizations(this.context.getLocalizations())
                    .localizeMode(IAdventureLocalizations.LocalizeMode.MM)
                    .localize("prefix")
                    .build());
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load messages", t);
        }

    }
}
