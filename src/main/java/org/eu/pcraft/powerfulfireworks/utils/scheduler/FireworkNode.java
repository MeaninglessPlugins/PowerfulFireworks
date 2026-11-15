package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public abstract class FireworkNode extends CommonNode{
    protected int flyTime;
    /** 该节点下每个烟花发射声的播放概率：0 全部静音，1 全部发声 */
    protected double launchChance = 0.1;
    /** 该节点下每个烟花爆炸声的播放概率：0 全部静音，1 全部发声 */
    protected double explosionChance = 0.2;
    final List<ItemStack> presets = new ArrayList<>();
    ThreadLocalRandom rd = ThreadLocalRandom.current();

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        this.flyTime = (int) section.getOrDefault("flyTime", 0);
        this.launchChance = ((Number) section.getOrDefault("launchChance", 0.1)).doubleValue();
        this.explosionChance = ((Number) section.getOrDefault("explosionChance", 0.2)).doubleValue();
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
            stack = FireworkUtil.getRandomFireworkItem(false);
        else
            stack = presets.get(rd.nextInt(presets.size()));
        return stack;
    }
    ItemStack getRandomPreset(boolean b) {
        ItemStack stack;
        if (presets.isEmpty())
            stack = FireworkUtil.getRandomFireworkItem(b);
        else
            stack = presets.get(rd.nextInt(presets.size()));
        return stack;
    }
}
