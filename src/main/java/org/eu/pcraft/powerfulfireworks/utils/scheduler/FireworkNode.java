package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public abstract class FireworkNode {
    protected ItemStack preset;

    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        if (section.containsKey("preset")) {
            String presetId = (String) section.get("preset");
            this.preset = Verify.verifyNotNull(scheduler.getPreset(presetId), "preset %s", presetId);
        }
    }

    public abstract void execute(FireworkStartupConfig config);
}
