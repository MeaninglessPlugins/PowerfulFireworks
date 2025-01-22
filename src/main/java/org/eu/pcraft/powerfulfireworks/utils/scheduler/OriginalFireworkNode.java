package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class OriginalFireworkNode extends FireworkNode {
    final List<ItemStack> presets = new ArrayList<>();
    boolean full = false;

    int count = 1;
    ThreadLocalRandom rd = ThreadLocalRandom.current();
    Interval<Double> X = new Interval<>(0.0,0.0);
    Interval<Double> Y = new Interval<>(0.0,0.0);
    Interval<Double> Z = new Interval<>(0.0,0.0);
    protected Interval<Double> getDoubleInterval(Map<String, Object> section, String key){
        return new Interval<>((LinkedHashMap<String, Double>) section.getOrDefault(key, new Interval<Double>(0.0, 0.0)));
    }
    protected Double getOffset(Interval<Double> interval){
        if(Objects.equals(interval.maximum, interval.minimum)){
            return interval.maximum;
        }
        return rd.nextDouble(interval.minimum, interval.maximum);
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
        for (int i = 0; i < count; i++) {
            ItemStack stack;
            if (this.full)
                stack = FireworkUtil.getRandomFireworkItem();
            else
                stack = presets.get(rd.nextInt(presets.size()));

            double xOff = getOffset(X);
            double yOff = getOffset(Y);
            double zOff = getOffset(Z);
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
