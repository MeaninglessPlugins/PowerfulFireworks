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
        this.flyTime = ((Number) section.getOrDefault("flyTime", 0)).intValue();
        this.launchChance = ((Number) section.getOrDefault("launchChance", 0.1)).doubleValue();
        this.explosionChance = ((Number) section.getOrDefault("explosionChance", 0.2)).doubleValue();
        // presets，允许写单个名字或名字列表
        Object presetSection = section.get("presets");
        if (presetSection instanceof String single) {
            addPreset(scheduler, single);
        } else if (presetSection instanceof List<?> list) {
            for (Object o : list) {
                addPreset(scheduler, String.valueOf(o));
            }
        }
    }

    private void addPreset(FireworkScheduler scheduler, String id) {
        ItemStack preset = scheduler.getPreset(id);
        if (preset == null)
            throw new IllegalArgumentException("Unknown preset " + id);
        presets.add(preset);
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
