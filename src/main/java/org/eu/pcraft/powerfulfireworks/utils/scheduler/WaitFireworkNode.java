package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import java.util.Map;

class WaitFireworkNode extends FireworkNode {
    long ticks = 0;

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        ticks = (long) section.getOrDefault("ticks", 0);
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        // implemented at scheduler
    }
}
