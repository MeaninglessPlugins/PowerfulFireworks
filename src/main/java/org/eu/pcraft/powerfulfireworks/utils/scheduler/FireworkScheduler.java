package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;
import de.tr7zw.nbtapi.NBT;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class FireworkScheduler {
    private static final Map<String, MethodHandle> nodeCompilers = new HashMap<>();

    @Getter
    @NotNull
    private final String id;
    @Getter
    private final boolean allowActivationByItems;

    private final Map<String, ItemStack> presets = new HashMap<>();
    private final List<CommonNode> nodes = new ArrayList<>();

    public ItemStack getPreset(String id) {
        return this.presets.get(id);
    }

    public void execute(FireworkStartupConfig config) {
        config.scheduler = this;
        PowerfulFireworks.getInstance().getSLF4JLogger().info("Executing scheduler {}", id);
        this.execute0(config, new AtomicInteger());
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        NBT.modify(item, w -> {
            w.setString(PowerfulFireworks.ITEM_KEY, this.id);
        });
        return item;
    }

    private void execute0(FireworkStartupConfig config, AtomicInteger state) {
        final PowerfulFireworks plugin = config.plugin;
        final Logger logger = config.plugin.getSLF4JLogger();
        config.updatePlayers();
        plugin.nextTick(() -> {
            int current = state.getAndIncrement();

            if (current < this.nodes.size()) {
                CommonNode node = this.nodes.get(current);

                if (plugin.mainConfig.debug)
                    logger.info("({}) executing node {}", this.id, node);
                if (node instanceof WaitFireworkNode wait) { // wait and next
                    plugin.runAfter(wait.ticks, () -> this.execute0(config, state));
                } else {
                    node.execute(config);   // execute now
                    this.execute0(config, state);    // schedule next
                }
            }
        });
    }

    @Override
    public String toString() {
        return "FireworkScheduler{id=" + this.id + ",presetsCount=" + this.presets.size() + ",nodes=" + this.nodes + "}";
    }

    public static FireworkScheduler compile(ConfigurationSection config) {
        FireworkScheduler scheduler = new FireworkScheduler(Verify.verifyNotNull(config.getString("id"), "Null scheduler ID"), config.getBoolean("allowActivationByItems", false));
        ConfigurationSection presets = Verify.verifyNotNull(config.getConfigurationSection("presets"), "presets in scheduler %s", scheduler.id);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) Verify.verifyNotNull(config.getList("nodes"), "nodes in scheduler %s", scheduler.id);

        // load presets
        for (String id : presets.getKeys(false)) {
            ConfigurationSection preset = Verify.verifyNotNull(presets.getConfigurationSection(id), "preset %s in scheduler %s", id, scheduler.id);
            List<Map<String, Object>> effects = Verify.verifyNotNull(((List<Map<String, Object>>) preset.getList("effects")), "effects in scheduler %s", scheduler.id);

            // compile effects
            List<FireworkEffect> compiledEffects = new ArrayList<>();
            for (Map<String, Object> effect : effects) {
                compiledEffects.add(FireworkScheduler.compileEffect(effect));
            }

            // construct to ItemStack
            ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
            FireworkMeta meta = (FireworkMeta) item.getItemMeta();
            meta.addEffects(compiledEffects);
            item.setItemMeta(meta);
            scheduler.presets.put(id, item);
        }

        // load nodes
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            String typeId = (String) Verify.verifyNotNull(node.get("type"), "type in scheduler %s index %s", scheduler.id, i);
            if (nodeCompilers.containsKey(typeId)) {    // compile
                try {
                    CommonNode inst = (CommonNode) nodeCompilers.get(typeId).invoke();
                    inst.load(scheduler, node);
                    scheduler.nodes.add(inst);
                } catch (Throwable e) {
                    throw new RuntimeException("Failed compiling node %s in scheduler %s index %s".formatted(typeId, scheduler.id, i), e);
                }
            } else {    // invalid node ID
                throw new IllegalArgumentException("Unknown node type %s in scheduler %s index %s".formatted(typeId, scheduler.id, i));
            }
        }

        return scheduler;
    }

    /**
     * Compile ConfigurationSection to FireworkEffect
     * @param section config
     * @return effect
     */
    private static FireworkEffect compileEffect(Map<String, Object> section) {
        FireworkEffect.Builder builder = FireworkEffect.builder();
        if (section.containsKey("colors"))
            builder.withColor(((List<String>) section.get("colors"))
                    .parallelStream()
                    .map(FireworkScheduler::color)
                    .collect(Collectors.toList())); // load colors
        if (section.containsKey("fade"))
            builder.withFade(((List<String>) section.get("fades"))
                    .parallelStream()
                    .map(FireworkScheduler::color)
                    .collect(Collectors.toList()));
        builder.flicker((boolean) section.getOrDefault("flicker", false));
        builder.trail((boolean) section.getOrDefault("trail", false));
        builder.with(FireworkEffect.Type.valueOf((String) section.getOrDefault("type", "BALL")));
        return builder.build();
    }

    /**
     * RGB color parser
     * @param rgb RGB
     * @return bukkit color
     */
    private static Color color(String rgb) {
        return Color.fromRGB(Integer.decode(rgb));
    }

    static void registerCompiler(String id, MethodHandle handle) {
        FireworkScheduler.nodeCompilers.put(id, handle);
    }

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType type = MethodType.methodType(void.class);
        try {
            registerCompiler("reference", lookup.findConstructor(ReferenceFireworkNode.class, type));
            registerCompiler("original", lookup.findConstructor(OriginalFireworkNode.class, type));
            registerCompiler("text", lookup.findConstructor(TextFireworkNode.class, type));
            registerCompiler("wait", lookup.findConstructor(WaitFireworkNode.class, type));
        } catch (Throwable e) {
            throw new RuntimeException("Failed to initialize compilers", e);
        }
    }
}
