package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;
import org.bukkit.block.BlockFace;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.sender.TextFirework;

import java.util.*;

class TextFireworkNode extends FireworkNode {
    protected boolean rotate = false;
    protected String[] lines = new String[0];
    protected double size = 1;
    protected BlockFace face;

    protected int total = 0;
    TextFirework firework;

    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        super.load(scheduler, section);
        this.rotate = (Boolean) section.getOrDefault("rotate", false);

        // get size
        this.size = (double) section.getOrDefault("size", 1.0);

        // get font
        String f = (String) Verify.verifyNotNull(section.get("font"), "font");
        BitmapFont font = Verify.verifyNotNull(PowerfulFireworks.getInstance().getFonts().get(f), "font %s", f);

        // precalculate lines
        String text = (String) Verify.verifyNotNull(section.get("text"), "text");
        int gap = (int) section.getOrDefault("gap", 1);
        this.lines = font.fromString(text, gap).chars();

        // count total characters
        this.total = 0;
        for (String line : this.lines) {
            this.total += line.length() - line.replace("1", "").length();
        }

        // get face for no rotate
        this.face = BlockFace.valueOf((String) section.getOrDefault("face", "EAST"));

        if(this.rotate){
            firework = new TextFirework(font, text, gap, this.size);
        }
        else {
            firework = new TextFirework(font, text, gap, this.size, this.face);
        }
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        firework.execute(flyTime, getRandomPreset(true), config.startupLocation, config.players);
    }

}
