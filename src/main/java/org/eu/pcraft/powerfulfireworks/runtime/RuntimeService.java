package org.eu.pcraft.powerfulfireworks.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.eu.pcraft.powerfulfireworks.script.CompiledScript;
import org.eu.pcraft.powerfulfireworks.script.RunHandle;
import org.eu.pcraft.powerfulfireworks.script.RunStarter;
import org.eu.pcraft.powerfulfireworks.script.RunningScript;
import org.eu.pcraft.powerfulfireworks.script.ScriptHost;
import org.slf4j.Logger;

/**
 * 运行中的脚本：活动表 + 每 tick 一个驱动任务。
 *
 * <p>只为"正在跑的脚本"服务，不参与 YAML 演出与配置加载。驱动任务在表空时立即取消，
 * 所以空闲时服务器上没有任何属于本插件的周期任务。
 */
public final class RuntimeService {

    /**
     * 调用链的深度上限。
     *
     * <p>脚本可以互相调用，但同一条链上每一层都在同一 tick 里占预算；
     * 没有上限时一个写错的递归会在一次 tick 里把整个服务器的帧时间吃光。
     */
    private static final int MAX_DEPTH = 8;

    /** 同时运行的脚本上限。 */
    private static final int MAX_RUNS = 64;

    /** 同一 tick 内最多推进几趟；一条 join 链在一个 tick 里全部结束也不会超过深度上限。 */
    private static final int MAX_PASSES_PER_TICK = MAX_DEPTH + 1;

    /**
     * 一个在跑的脚本及其来历。
     *
     * @param script  脚本 id，用于认出"同一个脚本又出现了"
     * @param parent  父运行的 id，顶层运行为 null
     * @param seed    随机种子；子运行的种子由父种子派生，所以一场演出可复现
     * @param lineage 从根到自身的脚本 id 链，用于递归与深度检查
     */
    private record Active(RunningScript run, String script, String parent, long seed, List<String> lineage) {
    }

    private final Plugin plugin;
    private final Logger logger;
    private final Function<String, CompiledScript> registry;
    private final Map<String, Active> runs = new LinkedHashMap<>();
    private BukkitTask driver;
    private long seedCounter;
    private long childCounter;
    /** 每次 {@link #stopAll()} 自增一次，宿主用它判断飞行中的爆炸是否已经作废。 */
    private long stopGeneration;

    public RuntimeService(Plugin plugin, Logger logger, Function<String, CompiledScript> registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.registry = registry;
    }

    /**
     * 用指定种子启动，用于复现某一次演出。
     *
     * @param seed 随机种子；传 {@code null} 表示每次都不一样
     */
    public RunningScript start(String id, CompiledScript script, ScriptHost host,
                               Map<String, Object> params, Long seed) {
        if (runs.size() >= MAX_RUNS) {
            throw new IllegalStateException("同时运行的脚本已达上限 " + MAX_RUNS);
        }
        return startRun(null, id, script.id(), script, host, params, seed, List.of(script.id()));
    }

    private RunningScript startRun(String parentId, String id, String scriptId, CompiledScript script,
                                   ScriptHost host, Map<String, Object> params, Long seed,
                                   List<String> lineage) {
        if (runs.containsKey(id)) {
            throw new IllegalArgumentException("运行 " + id + " 已经在跑");
        }
        long actualSeed = seed != null ? seed : ++seedCounter * 0x9E3779B97F4A7C15L + System.nanoTime();
        RunningScript run = script.start(host, childStarter(id, host), params, actualSeed);
        runs.put(id, new Active(run, scriptId, parentId, actualSeed, lineage));
        ensureDriver();
        return run;
    }

    /** 绑定到某一次运行的"启动子脚本"入口：子运行的命名与检查都以它为父。 */
    private RunStarter childStarter(String runId, ScriptHost host) {
        return (scriptId, params) -> startChild(runId, scriptId, host, params);
    }

    private RunHandle startChild(String parentId, String scriptId, ScriptHost parentHost,
                                 Map<String, Object> params) {
        CompiledScript script = registry.apply(scriptId);
        if (script == null) {
            logger.warn("脚本 {} 调用了不存在的脚本 {}", parentId, scriptId);
            return null;
        }
        Active parent = runs.get(parentId);
        if (parent == null) {
            logger.warn("脚本 {} 的父运行已经结束，拒绝启动 {}", parentId, scriptId);
            return null;
        }
        if (runs.size() >= MAX_RUNS) {
            logger.warn("同时运行的脚本已达上限 {}，拒绝启动 {}", MAX_RUNS, scriptId);
            return null;
        }
        if (!mayStart(parent, scriptId)) {
            return null;
        }
        long index = ++childCounter;
        // 子种子由父种子与序号派生：给父一个种子，整条调用链就都确定了
        long childSeed = parent.seed() * 0x9E3779B97F4A7C15L + index;
        String childId = parentId + "/" + scriptId + "#" + index;
        List<String> lineage = new ArrayList<>(parent.lineage());
        lineage.add(scriptId);
        return startRun(parentId, childId, scriptId, script, parentHost.child(), params, childSeed, lineage);
    }

    /**
     * 递归与深度检查。
     *
     * <p>递归的判断依据是脚本 id 而不是运行 id：A 调用 A 时两次运行的 id 不同，
     * 但如果脚本相同，这一层就一定不会自己结束。
     */
    private boolean mayStart(Active parent, String scriptId) {
        if (parent.lineage().contains(scriptId)) {
            logger.warn("脚本 {} 调用 {} 会形成递归，已拒绝", parent.run().id(), scriptId);
            return false;
        }
        if (parent.lineage().size() >= MAX_DEPTH) {
            logger.warn("脚本 {} 的调用链超过 {} 层，已拒绝继续调用 {}", parent.run().id(), MAX_DEPTH, scriptId);
            return false;
        }
        return true;
    }

    /** 停止一个运行及其全部子孙。 @return 之前是否存在这个运行 */
    public boolean stop(String id) {
        if (!runs.containsKey(id)) {
            return false;
        }
        String prefix = id + "/";
        for (String child : new ArrayList<>(runs.keySet())) {
            if (child.startsWith(prefix)) {
                cancel(child);
            }
        }
        cancel(id);
        stopDriverIfIdle();
        return true;
    }

    /** @return 当前停止代数；宿主在排期爆炸时捕获它 */
    public long stopGeneration() {
        return stopGeneration;
    }

    /** @return 被取消的运行数量 */
    public int stopAll() {
        stopGeneration++;
        int count = 0;
        for (String id : new ArrayList<>(runs.keySet())) {
            cancel(id);
            count++;
        }
        stopDriverIfIdle();
        return count;
    }

    private void cancel(String id) {
        Active active = runs.remove(id);
        if (active != null) {
            active.run().cancel();
        }
    }

    private void ensureDriver() {
        if (driver == null) {
            driver = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopDriverIfIdle() {
        if (driver != null && runs.isEmpty()) {
            driver.cancel();
            driver = null;
        }
    }

    private void tick() {
        if (runs.isEmpty()) {
            stopDriverIfIdle();
            return;
        }
        long now = Bukkit.getCurrentTick();
        // 一趟只推进"本趟开始时已存在"的运行。子运行在本趟里结束时，正在等它的父运行
        // 已经错过了这一趟，所以只要本趟有运行结束就再来一趟——pass 上限与调用链深度一致。
        for (int pass = 0; pass < MAX_PASSES_PER_TICK; pass++) {
            int before = runs.size();
            List<String> finished = null;
            for (Map.Entry<String, Active> entry : new ArrayList<>(runs.entrySet())) {
                RunningScript run = entry.getValue().run();
                try {
                    run.tick(now);
                } catch (Throwable t) {
                    // tick 内部已经把脚本异常记成 FAILED；这里兜住的是运行时自身的意外，
                    // 单个运行出错不该连累同一 tick 的其它运行。
                    logger.error("脚本 {} 推进失败", entry.getKey(), t);
                    run.cancel();
                }
                if (run.finished()) {
                    if (finished == null) {
                        finished = new ArrayList<>();
                    }
                    finished.add(entry.getKey());
                }
            }
            if (finished == null && runs.size() == before) {
                break;   // 这一趟没有结束、也没有新运行，不会再有人被唤醒
            }
            if (finished != null) {
                for (String id : finished) {
                    Active active = runs.remove(id);
                    if (active != null && active.run().state() == RunHandle.State.FAILED) {
                        logger.error("脚本 {} 执行失败", id, active.run().progress().failure());
                    }
                }
            }
        }
        stopDriverIfIdle();
    }
}
