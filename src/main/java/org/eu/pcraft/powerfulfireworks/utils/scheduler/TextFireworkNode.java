package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;

import java.util.Map;

class TextFireworkNode extends SingleFireworkNode {
    protected String text = "";

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        super.load(scheduler, section);
        this.text = (String) Verify.verifyNotNull(section.get("text"), "text");
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        // TODO: text firework
    }
}
