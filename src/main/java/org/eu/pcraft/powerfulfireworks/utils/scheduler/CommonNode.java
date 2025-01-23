package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import java.util.Map;

public abstract class CommonNode {

    protected abstract void load(FireworkScheduler scheduler, Map<String, Object> section);

    public abstract void execute(FireworkStartupConfig config);
}
