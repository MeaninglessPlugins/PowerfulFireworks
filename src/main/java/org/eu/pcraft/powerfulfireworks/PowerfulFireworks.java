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
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.eu.pcraft.powerfulfireworks.commands.MainCommand;
import org.eu.pcraft.powerfulfireworks.commands.TestCommand;
import org.eu.pcraft.powerfulfireworks.config.ConfigManager;
import org.eu.pcraft.powerfulfireworks.config.DiagnosticReporter;
import org.eu.pcraft.powerfulfireworks.config.MessagesConfigModule;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.config.ValidationRules;
import org.eu.pcraft.powerfulfireworks.hook.FireworkItemListener;
import org.eu.pcraft.powerfulfireworks.hook.VaultHook;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;
import org.eu.pcraft.powerfulfireworks.nms.NMSSelector;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.SchedulerValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class PowerfulFireworks extends JavaPlugin {

    public static NamespacedKey ITEM_KEY;
    public static NamespacedKey TOGGLE_KEY;

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
    private PepperConfigModule mainConfig = new PepperConfigModule();
    @Getter private Map<String, BitmapFont> fonts;
    @Getter private Map<String, FireworkScheduler> schedulers;

    @Getter VaultHook vaultHook = new VaultHook();

    private MainCommand mainCommand;

    FireworksTimer timer;
    @Getter private Set<Player> playerSet = new HashSet<>();

    @Override
    public void onLoad() {
        //brand
        showBrand();

        //instance
        PowerfulFireworks.instance = this;
        this.context = new BukkitPluginContext(this);

        ITEM_KEY = new NamespacedKey(instance, "powerfulfireworks-type");
        TOGGLE_KEY = new NamespacedKey(instance, "powerfulfireworks-toggle");

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
        Bukkit.getPluginManager().registerEvents(new FireworkItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new JoinQuitListener(), this);

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
            timer.cancel();
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
        loadAll(getDataFolder().toPath(), false);
    }

    /**
     * 只读校验：不写回配置文件，也不替换运行时的字体与演出。
     *
     * @return 本次校验的诊断，调用方可以直接读错误与警告数量
     */
    public DiagnosticReporter checkConfigurations() {
        return loadAll(getDataFolder().toPath(), true);
    }

    /**
     * 装载并校验全部配置。
     *
     * @param dryRun 为 true 时不落盘、不替换运行时状态，只产出诊断
     */
    private DiagnosticReporter loadAll(Path dataPath, boolean dryRun) {
        DiagnosticReporter reporter = new DiagnosticReporter(getSLF4JLogger(), false);

        try {
            ConfigManager<PepperConfigModule> configManager = new ConfigManager<>(
                    dataPath.resolve("config.yml"), PepperConfigModule.class, getSLF4JLogger(),
                    reporter, ValidationRules::validate);
            configManager.loadConfig(!dryRun);
            PepperConfigModule main = configManager.getConfigModule();
            reporter.setVerbose(main.debug);

            ConfigManager<MessagesConfigModule> messagesManager = new ConfigManager<>(
                    dataPath.resolve("messages.yml"), MessagesConfigModule.class, getSLF4JLogger(), reporter);
            messagesManager.loadConfig(!dryRun);

            if (!dryRun) {
                this.configManager = configManager;
                this.messagesManager = messagesManager;
                this.mainConfig = main;
                this.messageConfig = messagesManager.getConfigModule();
                // 本地化直接从磁盘读 messages.yml，只在正常装载时重建
                setupLocalizations();
            }

            Map<String, BitmapFont> fonts = loadFonts(dataPath, main, reporter, dryRun);
            checkTextFont(main, fonts, reporter);

            Map<String, FireworkScheduler> schedulers =
                    SchedulerValidator.load(dataPath.resolve("fireworks"), fonts.keySet(), reporter);

            if (!dryRun) {
                this.fonts = fonts;
                this.schedulers = schedulers;
                getSLF4JLogger().info("Loaded {} firework schedulers", schedulers.size());
                updateCommandCompletions();
            }

            reporter.report();
            return reporter;
        } catch (Exception e) {
            // 捕获异常
            this.getSLF4JLogger().error("Critical error loading configurations", e);
            throw new RuntimeException("Failed to load plugin configurations", e);
        }
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

    private Map<String, BitmapFont> loadFonts(Path dataPath, PepperConfigModule config,
                                              DiagnosticReporter reporter, boolean dryRun) {
        Path folder = dataPath.resolve("fonts");
        Map<String, BitmapFont> loaded = new HashMap<>();

        if (!Files.exists(folder)) {
            if (dryRun) {
                return loaded;
            }
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                throw new RuntimeException("Unable to initialize fonts directory", e);
            }
        }

        for (Map.Entry<String, String> entry : config.fonts.entrySet()) {
            loadSingleFont(folder, entry.getKey(), entry.getValue(), loaded, reporter, dryRun);
        }
        return loaded;
    }

    private void loadSingleFont(Path folder, String fontId, String fileName,
                                Map<String, BitmapFont> loaded, DiagnosticReporter reporter, boolean dryRun) {
        Path path = folder.resolve(fileName);
        try {
            if (Files.isRegularFile(path)) {
                String bdfContent = Files.readString(path, StandardCharsets.UTF_8);
                loaded.put(fontId.toLowerCase(Locale.ROOT), BitmapFont.parseBDF(bdfContent));
                if (!dryRun) {
                    this.getSLF4JLogger().info("Loaded font {} from {}", fontId, path);
                }
            } else {
                reporter.add("fonts", List.of(new ConfigIssue(IssueLevel.ERROR, fontId,
                        "字体文件 " + fileName + " 不存在，使用该字体的演出无法加载",
                        "把文件放进 fonts 文件夹")));
            }
        } catch (Throwable t) {
            reporter.add("fonts", List.of(new ConfigIssue(IssueLevel.ERROR, fontId,
                    "字体文件 " + fileName + " 解析失败：" + t.getMessage(), "确认是 BDF 格式")));
        }
    }

    /** 文本烟花引用的字体必须在 fonts 段里且成功加载，否则运行时只会静默退回普通烟花。 */
    private void checkTextFont(PepperConfigModule config, Map<String, BitmapFont> fonts, DiagnosticReporter reporter) {
        PepperConfigModule.RandomFirework random = config.randomFirework;
        if (random == null || random.text == null || !random.enabled || !random.text.enabled) {
            return;
        }
        String font = random.text.font;
        if (font != null && !fonts.containsKey(font.toLowerCase(Locale.ROOT))) {
            reporter.add("config.yml", List.of(new ConfigIssue(IssueLevel.ERROR, "randomFirework.text.font",
                    "字体 \"" + font + "\" 没有成功加载，文本烟花会退回普通烟花",
                    "检查 fonts 段与该文件是否存在")));
        }
    }

    private void updateCommandCompletions() {
        if (this.mainCommand != null) {
            this.mainCommand.setFontIdComp(this.fonts.keySet().toArray(new String[0]));
            this.mainCommand.setFireworkComp(this.schedulers.keySet().toArray(new String[0]));
        }
    }

    public void toggleFirework(Player player, boolean value) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(PowerfulFireworks.TOGGLE_KEY, PersistentDataType.BOOLEAN, value);
        updateToggle(player);
    }
    public void updateToggle(Player player){
        PersistentDataContainer container = player.getPersistentDataContainer();
        if(!container.has(PowerfulFireworks.TOGGLE_KEY)){
            if(instance.getMainConfig().randomFirework.turnOnByDefault){
                container.set(PowerfulFireworks.TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
            }
            else{
                container.set(PowerfulFireworks.TOGGLE_KEY, PersistentDataType.BOOLEAN, false);
            }
        }
        if(container.get(PowerfulFireworks.TOGGLE_KEY, PersistentDataType.BOOLEAN).equals(true)){
            instance.getPlayerSet().add(player);
        }
        else{
            instance.getPlayerSet().remove(player);
        }
    }
    public void applyConfigurations(){
        // RandomFireworks
        if(timer != null){
            timer.cancel();
        }
        playerSet.clear();
        if(mainConfig.randomFirework.enabled){
            for(Player p:Bukkit.getOnlinePlayers()){
                updateToggle(p);
            }
            timer = new FireworksTimer(mainConfig.randomFirework.delay);
            timer.start();
        }
    }
}
