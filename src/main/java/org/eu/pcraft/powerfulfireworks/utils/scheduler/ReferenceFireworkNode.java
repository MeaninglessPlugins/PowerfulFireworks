package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;

import java.util.Map;

public class ReferenceFireworkNode extends FireworkNode{
    private String target;

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        super.load(scheduler, section);
        this.target = (String) Verify.verifyNotNull(section.get("target"));
        if (this.target.equals(scheduler.getId()))
            throw new IllegalArgumentException("Circular scheduler reference: " + this.target);
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        FireworkScheduler scheduler = config.plugin.getSchedulers().get(this.target);
        if (scheduler == null)
            config.plugin.getSLF4JLogger().warn("Unknown scheduler reference: {}", this.target);
        else
            scheduler.execute(config);
    }
}
