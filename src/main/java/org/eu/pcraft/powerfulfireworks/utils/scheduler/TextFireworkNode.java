package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
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
        this.lines = font.fromString(text, gap).getChars();

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

    //to Delete
    private void sendRotate(NMSProvider nms, FireworkStartupConfig config, int[] id, UUID[] uuid) {
        for(Player p: config.players){
            FireworkUtil.sendRotateTextFireworks(nms, p, config.startupLocation, this.size, this.lines, id, uuid);
        }
    }

    private void send(NMSProvider nms, FireworkStartupConfig config, int[] id, UUID[] uuid) {
        double xOff = this.face.getModX() * this.size;
        double zOff = this.face.getModZ() * this.size;

        // calculate startup location
        Location loc = config.startupLocation;
        double sx = loc.getX() - ((xOff * this.lines[0].length()) / 2.0);
        double sy = loc.getY();
        double sz = loc.getZ() - ((zOff * this.lines[0].length()) / 2.0);

        double nx = sx;
        double nz = sz;

        int totalEnt = 0;

        for (String line : this.lines) {
            for (char aChar : line.toCharArray()) {
                // x and z offset
                nx += xOff;
                nz += zOff;

                if (aChar == '0') {  // skip empty chars
                    continue;
                }

                Location nLocation = new Location(loc.getWorld(), nx, sy, nz);
                // send
                NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id[totalEnt], uuid[totalEnt], nLocation);
                NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id[totalEnt], getRandomPreset());
                for (Player player : config.players) {
                    nms.sendAddEntity(player, add, data);
                }
                totalEnt++;
            }

            // reset X and Z
            nx = sx;
            nz = sz;

            // y offset
            sy -= this.size;
        }
    }
}
