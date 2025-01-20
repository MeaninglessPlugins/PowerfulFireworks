package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RandomFireworkNode extends FireworkNode {
    final List<ItemStack> presets = new ArrayList<>();
    boolean single = false;
    boolean full = false;

    int count = 1;
    double maxX = 0.0;
    double maxY = 0.0;
    double maxZ = 0.0;
    double minX = 0.0;
    double minY = 0.0;
    double minZ = 0.0;

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        this.full = (boolean) section.getOrDefault("full", false);  // enable full random mode
        if (!this.full) {
            // presets
            List<String> pr = (List<String>) section.get("presets");
            if (pr != null) {
                for (String s : pr) {
                    ItemStack p = scheduler.getPreset(s);
                    if (p == null)
                        throw new IllegalArgumentException("preset " + s);
                    presets.add(p);
                }
            }
            single = this.presets.size() != 1;
        }

        this.count = (int) section.getOrDefault("count", 1);
        this.maxX = (double) section.getOrDefault("maxX", 0.0);
        this.maxY = (double) section.getOrDefault("maxY", 0.0);
        this.maxZ = (double) section.getOrDefault("maxZ", 0.0);
        this.minX = (double) section.getOrDefault("minX", 0.0);
        this.minY = (double) section.getOrDefault("minY", 0.0);
        this.minZ = (double) section.getOrDefault("minZ", 0.0);
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        ThreadLocalRandom rd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            ItemStack stack;
            if (this.single)
                stack = this.presets.get(0);
            else if (this.full)
                stack = FireworkUtil.getRandomFireworkItem();
            else
                stack = presets.get(rd.nextInt(presets.size()));

            double xOff = rd.nextDouble(minX, maxX);
            double yOff = rd.nextDouble(minY, maxY);
            double zOff = rd.nextDouble(minZ, maxZ);
            // send create and add to id list
            int[] id = new int[]{FireworkUtil.broadcastFireworkCreate(
                    config.players,
                    stack,
                    config.startupLocation.clone().add(xOff, yOff, zOff))};

            // make an explosion task
            BukkitRunnable fireworkExplosionTask = new BukkitRunnable() {
                @Override
                public void run() {
                    FireworkUtil.broadcastFireworkExplosion(config.players, id);
                }
            };
            fireworkExplosionTask.runTaskLater(config.plugin, flyTime);
        }
    }
}
