package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.Map;

class SingleFireworkNode extends FireworkNode {
    protected double xOff;
    protected double yOff;
    protected double zOff;


    @Override
    public void execute(FireworkStartupConfig config) {
        // send create and add to id list
        int[] id = new int[]{FireworkUtil.broadcastFireworkCreate(config.players, this.preset, config.startupLocation.clone().add(xOff, yOff, zOff))};
        
        // make an explosion task
        BukkitRunnable fireworkExplosionTask = new BukkitRunnable() {
            @Override
            public void run() {
                FireworkUtil.broadcastFireworkExplosion(config.players, id);
            }
        };
        fireworkExplosionTask.runTaskLater(config.plugin, flyTime);
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
                ", flyTime=" + flyTime +
                '}';
    }
}
