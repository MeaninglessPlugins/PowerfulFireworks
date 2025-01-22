package org.eu.pcraft.powerfulfireworks;

import cn.afternode.commons.bukkit.BukkitPluginContext;
import cn.afternode.commons.bukkit.ConfigurationLocalizations;
import cn.afternode.commons.bukkit.IAdventureLocalizations;
import cn.afternode.commons.bukkit.message.MessageBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.eu.pcraft.powerfulfireworks.commands.MainCommand;
import org.eu.pcraft.powerfulfireworks.commands.TestCommand;
import org.eu.pcraft.powerfulfireworks.config.ConfigManager;
import org.eu.pcraft.powerfulfireworks.config.MessagesConfigModule;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.hook.VaultHook;
import org.eu.pcraft.powerfulfireworks.nms.NMSSelector;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PowerfulFireworks extends JavaPlugin {
    @Getter
    private static PowerfulFireworks instance;

    @Getter
    private BukkitPluginContext context;
    @Getter
    private NMSProvider nms;

    @Getter
    private ConfigManager<PepperConfigModule> configManager;
    @Getter
    private ConfigManager<MessagesConfigModule> messagesManager;

    @Getter
    private MessagesConfigModule messageConfig = new MessagesConfigModule();
    @Getter
    public PepperConfigModule mainConfig = new PepperConfigModule();
    @Getter private Map<String, BitmapFont> fonts;
    @Getter private Map<String, FireworkScheduler> schedulers;

    @Getter VaultHook vaultHook = new VaultHook();

    private MainCommand mainCommand;

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
            getLogger().warning("You are using the DEBUG mode!");
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
        this.mainCommand = new MainCommand();
        map.register("fireworks", this.mainCommand);

        // Listener
        Bukkit.getPluginManager().registerEvents(new EventListener(), instance);

        // hook
        boolean isHookingSuccessfully = vaultHook.setup();
        if(isHookingSuccessfully){
            getLogger().info("Successfully hook into Vault");
        }
        else{
            getLogger().severe("Failed to hook into Vault");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        // others
        applyConfigurations();

    }

    @Override
    public void onDisable() {
        //stop timer
        if(timer!=null)
            timer.stop();
    }

    public void runAfter(long ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(this, runnable, ticks);
    }

    public void nextTick(Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }

    public void loadConfigurations() {
        Path dataPath = getDataFolder().toPath();

        //load
        configManager=new ConfigManager<>(dataPath.resolve("config.yml"), mainConfig);
        messagesManager=new ConfigManager<>(dataPath.resolve("messages.yml"), messageConfig);
        configManager.loadConfig();
        messagesManager.loadConfig();
        this.mainConfig = configManager.getConfigModule();

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

        // fonts
        Path folder = dataPath.resolve("fonts");
        try {
            this.fonts = new HashMap<>();
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }
            Map<String, String> conf = this.mainConfig.fonts;
            for (String id : conf.keySet()) {
                Path path = folder.resolve(conf.get(id));
                try {
                    if (Files.isRegularFile(path)) {
                        this.fonts.put(id.toLowerCase(Locale.ROOT), BitmapFont.parseBDF(Files.readString(path, StandardCharsets.UTF_8)));
                    } else {
                        this.getSLF4JLogger().warn("Invalid or missing font file {}: {}", id, conf.get(id));
                    }
                    this.getSLF4JLogger().info("Loaded font {} from {}", id, path);
                } catch (Throwable t) {
                    this.getSLF4JLogger().warn("Error loading font file {} for {}", conf.get(id), id, t);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load fonts", t);
        }

        // fireworks
        Path fwPath = dataPath.resolve("fireworks");
        this.schedulers = new HashMap<>();
        try {
            if (!Files.exists(fwPath))
                Files.createDirectories(fwPath);
            try (Stream<Path> paths = Files.list(fwPath)) {
                for (Path path : paths
                        .filter(it -> Files.isRegularFile(it) && it.toString().endsWith(".yml"))
                        .collect(Collectors.toSet())) {
                    try {
                        FireworkScheduler compiled = FireworkScheduler.compile(YamlConfiguration.loadConfiguration(Files.newBufferedReader(path)));
                        this.schedulers.put(compiled.getId(), compiled);
                        this.getSLF4JLogger().info("Compiled {} from {}", compiled.getId(), path);
                    } catch (Throwable e) {
                        this.getSLF4JLogger().warn("Failed compiling firework from {}", path, e);
                    }
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load firework schedulers", t);
        }
        this.getSLF4JLogger().info("Loaded {} firework schedulers", this.schedulers.size());


        if (this.mainCommand != null) {
            this.mainCommand.setFontIdComp(this.fonts.keySet().toArray(new String[0])); // Add to font ID completions
            this.mainCommand.setFireworkComp(this.schedulers.keySet().toArray(new String[0]));  // Add to firework completions
        }
    }
    public void applyConfigurations(){
        // timer
        if(timer!=null){
            timer.stop();
        }
        if(mainConfig.randomFirework.enabled){
            timer=new FireworksTimer(mainConfig.randomFirework.delay);
            timer.start();
        }
        // permission
        if(mainConfig.randomFirework.turnOnDefaultly){
            Permissions.SWITCHES_RANDOMFIREWORKS.setDefault(PermissionDefault.TRUE);
        }
        else{
            Permissions.SWITCHES_RANDOMFIREWORKS.setDefault(PermissionDefault.FALSE);
        }
    }
}
