package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import java.util.Map;

class WaitFireworkNode extends CommonNode {
    long ticks = 0;

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        ticks = ((Number) section.getOrDefault("ticks", 1)).longValue();
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        // implemented at scheduler
    }
}
