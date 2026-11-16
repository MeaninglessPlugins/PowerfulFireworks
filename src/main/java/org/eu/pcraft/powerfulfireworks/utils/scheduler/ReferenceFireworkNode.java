package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;

import java.util.Map;

public class ReferenceFireworkNode extends CommonNode {
    private String target;
    /** 是否等被引用的演出跑完再继续本演出，默认 false（即发起后立刻继续） */
    private boolean blocking;

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        this.target = (String) Verify.verifyNotNull(section.get("target"));
        Object rawBlocking = section.get("blocking");
        this.blocking = rawBlocking instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(rawBlocking));
        if (this.target.equals(scheduler.getId()))
            throw new IllegalArgumentException("Circular scheduler reference: " + this.target);
    }

    public boolean isBlocking() {
        return this.blocking;
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        FireworkScheduler scheduler = config.plugin.getSchedulers().get(this.target);
        if (scheduler == null)
            config.plugin.getSLF4JLogger().warn("Unknown scheduler reference: {}", this.target);
        else
            scheduler.execute(config);
    }

    /**
     * 阻塞执行：被引用的演出整场跑完后才调用 {@code next}。
     *
     * <p>目标不存在时记一条警告并立刻继续，免得父演出卡死在这里。</p>
     */
    public void executeBlocking(FireworkStartupConfig config, Runnable next) {
        FireworkScheduler scheduler = config.plugin.getSchedulers().get(this.target);
        if (scheduler == null) {
            config.plugin.getSLF4JLogger().warn("Unknown scheduler reference: {}", this.target);
            next.run();
            return;
        }
        scheduler.execute(config, next);
    }
}
