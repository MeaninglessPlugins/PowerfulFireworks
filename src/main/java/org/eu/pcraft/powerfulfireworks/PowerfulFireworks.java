package org.eu.pcraft.powerfulfireworks;

import cn.afternode.commons.bukkit.BukkitPluginContext;
import cn.afternode.commons.bukkit.ConfigurationLocalizations;
import cn.afternode.commons.bukkit.IAdventureLocalizations;
import cn.afternode.commons.bukkit.message.MessageBuilder;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.eu.pcraft.powerfulfireworks.commands.MainCommand;
import org.eu.pcraft.powerfulfireworks.commands.TestCommand;
import org.eu.pcraft.powerfulfireworks.config.ConfigManager;
import org.eu.pcraft.powerfulfireworks.config.MessagesConfigModule;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.hook.FireworkItemListener;
import org.eu.pcraft.powerfulfireworks.hook.VaultHook;
import org.eu.pcraft.powerfulfireworks.nms.NMSSelector;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class PowerfulFireworks extends JavaPlugin {

    public static NamespacedKey ITEM_KEY;

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
        //brand
        showBrand();

        //instance
        PowerfulFireworks.instance = this;
        ITEM_KEY = new NamespacedKey(instance, "powerfulfireworks-type");
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

        // bStats
        int pluginId = 24294;
        Metrics metrics = new Metrics(this, pluginId);

        // Commands
        registerCommands();

        // Listeners
        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        Bukkit.getPluginManager().registerEvents(new FireworkItemListener(), this);

        // Hooks
        boolean vaultHookingSuccessfully = vaultHook.setup();
        if(vaultHookingSuccessfully){
            getLogger().info("Successfully hook into Vault!");
        }
        else{
            getLogger().severe("Failed to hook into Vault!");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        // Others
        applyConfigurations();

        // Permissions
        Permissions.register();
    }

    @Override
    public void onDisable() {
        //stop timer
        if(timer != null)
            timer.stop();
        getLogger().info("PowerfulFireworks has been disabled.");
    }

    private void showBrand(){
        getLogger().info("""
                
                
                  ____                                      __           _      \s
                 |  _ \\    ___   __      __   ___   _ __   / _|  _   _  | |     \s
                 | |_) |  / _ \\  \\ \\ /\\ / /  / _ \\ | '__| | |_  | | | | | |     \s
                 |  __/  | (_) |  \\ V  V /  |  __/ | |    |  _| | |_| | | |     \s
                 |_|      \\___/    \\_/\\_/    \\___| |_|    |_|    \\__,_| |_|     \s
                  _____   _                                           _         \s
                 |  ___| (_)  _ __    ___  __      __   ___    _ __  | | __  ___\s
                 | |_    | | | '__|  / _ \\ \\ \\ /\\ / /  / _ \\  | '__| | |/ / / __|
                 |  _|   | | | |    |  __/  \\ V  V /  | (_) | | |    |   <  \\__ \\
                 |_|     |_| |_|     \\___|   \\_/\\_/    \\___/  |_|    |_|\\_\\ |___/
                                                                                \s
                Thanks for using PowerfulFireworks!
                Written by Zyklone & jlxnb
                """);
    }

    private void registerCommands() {
        CommandMap map = Bukkit.getCommandMap();
        this.mainCommand = new MainCommand(this);

        map.register("testfireworks", new TestCommand());
        map.register("powerfulfireworks", this.mainCommand);
    }

    public void runAfter(long ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(this, runnable, ticks);
    }

    public void nextTick(Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }

    public void loadConfigurations() {
        Path dataPath = getDataFolder().toPath();

        try {
            // 配置加载
            loadBaseConfigs(dataPath);

            // 本地化
            setupLocalizations();

            // 资源加载
            loadFonts(dataPath);
            loadFireworks(dataPath);

            // 更新命令补全
            updateCommandCompletions();

        } catch (Exception e) {
            // 捕获异常
            this.getSLF4JLogger().error("Critical error loading configurations", e);
            throw new RuntimeException("Failed to load plugin configurations", e);
        }
    }

    private void loadBaseConfigs(Path dataPath) {
        this.configManager = new ConfigManager<>(dataPath.resolve("config.yml"), mainConfig);
        this.messagesManager = new ConfigManager<>(dataPath.resolve("messages.yml"), messageConfig);

        this.configManager.loadConfig();
        this.messagesManager.loadConfig();

        this.mainConfig = this.configManager.getConfigModule();
    }

    private void setupLocalizations() {
        try {
            this.context.setLocalizations(new ConfigurationLocalizations(this.context.loadConfiguration("messages.yml")));
            this.context.setDefaultLocalizeMode(IAdventureLocalizations.LocalizeMode.MM);

            // 构建并设置前缀
            var prefixComponent = new MessageBuilder()
                    .localizations(this.context.getLocalizations())
                    .localizeMode(IAdventureLocalizations.LocalizeMode.MM)
                    .localize("prefix")
                    .build();

            this.context.setMessageLinePrefix(prefixComponent);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load messages/localizations", t);
        }
    }

    private void loadFonts(Path dataPath) {
        Path folder = dataPath.resolve("fonts");
        this.fonts = new HashMap<>();

        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }

            Map<String, String> fontConfig = this.mainConfig.fonts;
            for (Map.Entry<String, String> entry : fontConfig.entrySet()) {
                String fontId = entry.getKey();
                String fileName = entry.getValue();
                loadSingleFont(folder, fontId, fileName);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize fonts directory", e);
        }
    }

    private void loadSingleFont(Path folder, String fontId, String fileName) {
        Path path = folder.resolve(fileName);
        try {
            if (Files.isRegularFile(path)) {
                String bdfContent = Files.readString(path, StandardCharsets.UTF_8);
                this.fonts.put(fontId.toLowerCase(Locale.ROOT), BitmapFont.parseBDF(bdfContent));
                this.getSLF4JLogger().info("Loaded font {} from {}", fontId, path);
            } else {
                this.getSLF4JLogger().warn("Invalid or missing font file {}: {}", fontId, fileName);
            }
        } catch (Throwable t) {
            this.getSLF4JLogger().warn("Error loading font file {} for {}", fileName, fontId, t);
        }
    }

    private void loadFireworks(Path dataPath) {
        Path fwPath = dataPath.resolve("fireworks");
        this.schedulers = new HashMap<>();

        try {
            if (!Files.exists(fwPath)) {
                Files.createDirectories(fwPath);
            }

            try (Stream<Path> paths = Files.list(fwPath)) {
                paths.filter(this::isYamlFile)
                        .forEach(this::compileAndRegisterFirework);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load firework schedulers", t);
        }
        this.getSLF4JLogger().info("Loaded {} firework schedulers", this.schedulers.size());
    }

    private boolean isYamlFile(Path path) {
        String name = path.toString();
        return Files.isRegularFile(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private void compileAndRegisterFirework(Path path) {
        try {
            var yamlConfig = YamlConfiguration.loadConfiguration(Files.newBufferedReader(path));
            FireworkScheduler compiled = FireworkScheduler.compile(yamlConfig);
            this.schedulers.put(compiled.getId(), compiled);
            this.getSLF4JLogger().info("Compiled {} from {}", compiled.getId(), path);
        } catch (Throwable e) {
            this.getSLF4JLogger().warn("Failed compiling firework from {}", path, e);
        }
    }

    private void updateCommandCompletions() {
        if (this.mainCommand != null) {
            this.mainCommand.setFontIdComp(this.fonts.keySet().toArray(new String[0]));
            this.mainCommand.setFireworkComp(this.schedulers.keySet().toArray(new String[0]));
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
        if(mainConfig.randomFirework.turnOnByDefault){
            Permissions.SWITCHES_RANDOMFIREWORKS.setDefault(PermissionDefault.TRUE);
        }
        else{
            Permissions.SWITCHES_RANDOMFIREWORKS.setDefault(PermissionDefault.FALSE);
        }
    }
}
