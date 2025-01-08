package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import java.util.Map;

class SingleFireworkNode extends FireworkNode {
    protected double xOff;
    protected double yOff;
    protected double zOff;

    @Override
    public void execute(FireworkStartupConfig config) {
        // TODO: implement firework execution
    }

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        super.load(scheduler, section);
        this.xOff = (double) section.getOrDefault("xOff", 0.0);
        this.yOff = (double) section.getOrDefault("yOff", 0.0);
        this.zOff = (double) section.getOrDefault("zOff", 0.0);
    }

    @Override
    public String toString() {
        return "SingleFireworkNode{" +
                "xOff=" + xOff +
                ", yOff=" + yOff +
                ", zOff=" + zOff +
                '}';
    }
}
