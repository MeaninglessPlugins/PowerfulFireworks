package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

class OriginalFireworkNode extends FireworkNode {
    final List<ItemStack> presets = new ArrayList<>();
    boolean full = false;

    int count = 1;
    Interval<Double> X = new Interval<>(0.0,0.0);
    Interval<Double> Y = new Interval<>(0.0,0.0);
    Interval<Double> Z = new Interval<>(0.0,0.0);
    protected Interval<Double> getDoubleInterval(Map<String, Object> section, String key){
        return new Interval<>((LinkedHashMap<String, Double>) section.getOrDefault(key, new Interval<Double>(0.0, 0.0)));
    }
    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
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
        this.full = this.presets.isEmpty();
        this.count = (int) section.getOrDefault("count", 1);
        this.X = getDoubleInterval(section,"XOff");
        this.Y = getDoubleInterval(section,"YOff");
        this.Z = getDoubleInterval(section,"ZOff");
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        ThreadLocalRandom rd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            ItemStack stack;
            if (this.full)
                stack = FireworkUtil.getRandomFireworkItem();
            else
                stack = presets.get(rd.nextInt(presets.size()));

            double xOff = rd.nextDouble(X.minimum, X.maximum);
            double yOff = rd.nextDouble(Y.minimum, Y.maximum);
            double zOff = rd.nextDouble(Z.minimum, Z.maximum);
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
