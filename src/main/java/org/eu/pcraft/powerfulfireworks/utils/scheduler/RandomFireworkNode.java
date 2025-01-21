package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RandomFireworkNode extends FireworkNode {
    final List<ItemStack> presets = new ArrayList<>();
    boolean single = false;
    boolean full = false;

    int count = 1;
    Pair<Double> X = new Pair<>(0.0,0.0);
    Pair<Double> Y = new Pair<>(0.0,0.0);
    Pair<Double> Z = new Pair<>(0.0,0.0);
    protected Pair<Double> getDoubleInterval(Map<String, Object> section, String key){
        return new Pair<>((LinkedHashMap<String, Double>) section.getOrDefault(key, new Pair<Double>(0.0, 0.0)));
    }
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
        this.X = getDoubleInterval(section,"X");
        this.Y = getDoubleInterval(section,"Y");
        this.Z = getDoubleInterval(section,"Z");
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
