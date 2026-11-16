package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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

    /** 单个 tick 内最多连续执行多少个节点，防止超长演出把一 tick 拖住 */
    private static final int NODES_PER_TICK = 64;

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

    /** 顶层入口：开一场新演出，并重置本 tick 的节点预算。 */
    public void execute(FireworkStartupConfig config) {
        config.nodeBudget = new NodeBudget();
        start(config, null);
    }

    /**
     * 执行整场演出。
     *
     * @param onComplete 所有节点跑完后的回调，可为 null。阻塞引用靠它把控制权交还父演出。
     */
    public void execute(FireworkStartupConfig config, Runnable onComplete) {
        // 被别的演出引用：沿用父演出这一 tick 剩余的节点预算，避免引用把单 tick 的量放大
        if (config.nodeBudget == null) {
            config.nodeBudget = new NodeBudget();
        }
        start(config, onComplete);
    }

    private void start(FireworkStartupConfig config, Runnable onComplete) {
        PowerfulFireworks.getInstance().getSLF4JLogger().info("Executing scheduler {}", id);
        AtomicInteger state = new AtomicInteger();
        if (Bukkit.isPrimaryThread()) {
            this.runPending(config, state, onComplete);
        } else {
            config.plugin.nextTick(() -> this.runPending(config, state, onComplete));
        }
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = PowerfulFireworks.ITEM_KEY;
        pdc.set(key, PersistentDataType.STRING, this.id);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 在当前 tick 里连续执行节点，直到需要等待（{@code wait} / 阻塞引用）、演出结束，或者用完
     * 本 tick 的节点预算。
     *
     * <p>预算的作用是兜底：一场演出如果写了几百个即时节点，不至于把单个 tick 拖住。</p>
     */
    private void runPending(FireworkStartupConfig config, AtomicInteger state, Runnable onComplete) {
        final PowerfulFireworks plugin = config.plugin;
        final Logger logger = plugin.getSLF4JLogger();
        config.updatePlayers();
        NodeBudget budget = config.nodeBudget;

        while (budget.consume()) {
            int current = state.getAndIncrement();
            if (current >= this.nodes.size()) {
                if (onComplete != null) onComplete.run();
                return;
            }

            CommonNode node = this.nodes.get(current);
            if (plugin.getMainConfig().debug)
                logger.info("({}) executing node {}", this.id, node);

            if (node instanceof WaitFireworkNode wait) { // 唯一的让出点
                plugin.runAfter(wait.ticks, () -> this.runPending(config, state, onComplete));
                return;
            }
            if (node instanceof ReferenceFireworkNode ref && ref.isBlocking()) {
                // 阻塞引用：对方跑完后直接接着走，不再多等一 tick
                try {
                    // 续跑时沿用同一份预算，跨 tick 的情况由 NodeBudget 自己判断
                    ref.executeBlocking(config, () -> this.runPending(config, state, onComplete));
                } catch (Throwable t) {
                    logger.warn("Blocking node {} of scheduler {} failed", node, this.id, t);
                }
                return;
            }

            try {
                node.execute(config);
            } catch (Throwable t) {
                // 单个节点出错就跳过它，别让整场演出停在这里
                logger.warn("Node {} of scheduler {} failed", node, this.id, t);
            }
        }

        // 预算用完，下一个 tick 接着跑；预算对象跨 tick 会自动重置
        plugin.nextTick(() -> this.runPending(config, state, onComplete));
    }

    /**
     * 一场演出在一个 tick 里还能跑多少个节点。
     *
     * <p>引用型节点共享父演出的这一份预算，按 {@link Bukkit#getCurrentTick() 服务端 tick} 自动重置，
     * 所以"单 tick 最多 64 个节点"对整条引用链成立，而不是每个演出、每一跳各算各的。</p>
     */
    public static final class NodeBudget {
        private int remaining = NODES_PER_TICK;
        private int tick = -1;

        /**
         * 取一个节点额度。跨 tick 时自动重置，所以不需要在续跑的地方手动换新预算。
         */
        boolean consume() {
            int now = Bukkit.getCurrentTick();
            if (now != this.tick) {
                this.tick = now;
                this.remaining = NODES_PER_TICK;
            }
            if (this.remaining <= 0) {
                return false;
            }
            this.remaining--;
            return true;
        }
    }

    /** 本演出引用到的其它演出 id，供装载期做跨文件校验。 */
    public Set<String> getReferenceTargets() {
        Set<String> targets = new HashSet<>();
        for (CommonNode node : this.nodes) {
            if (node instanceof ReferenceFireworkNode reference) {
                targets.add(reference.getTarget());
            }
        }
        return targets;
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
        Object fade = section.containsKey("fade") ? section.get("fade") : section.get("fades");
        if (fade != null)
            builder.withFade(((List<String>) fade)
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
