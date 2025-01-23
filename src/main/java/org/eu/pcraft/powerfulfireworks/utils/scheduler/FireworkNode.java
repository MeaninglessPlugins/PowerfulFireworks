package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public abstract class FireworkNode extends CommonNode{
    protected int flyTime;
    final List<ItemStack> presets = new ArrayList<>();
    ThreadLocalRandom rd = ThreadLocalRandom.current();

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        this.flyTime = (int) section.getOrDefault("flyTime", 0);
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
    }

    ItemStack getRandomPreset() {
        ItemStack stack;
        if (presets.isEmpty())
            stack = FireworkUtil.getRandomFireworkItem();
        else
            stack = presets.get(rd.nextInt(presets.size()));
        return stack;
    }
}
