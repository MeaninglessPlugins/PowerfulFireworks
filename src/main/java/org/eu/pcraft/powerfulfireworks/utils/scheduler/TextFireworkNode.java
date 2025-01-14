package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import com.google.common.base.Verify;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.*;

class TextFireworkNode extends SingleFireworkNode {
    protected boolean rotate = false;
    protected String[] lines = new String[0];
    protected double size = 1;
    protected BlockFace face;

    protected int total = 0;

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
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        NMSProvider nms = PowerfulFireworks.getInstance().getNms();

        // allocate IDs
        int[] id = new int[this.total + 1];
        UUID[] uuid = new UUID[this.total + 1];
        for (int i = 0; i < this.total + 1; i++) {
            id[i] = nms.allocateEntityId();
            uuid[i] = UUID.randomUUID();
        }

        if (this.rotate) {
            this.sendRotate(nms, config, id, uuid); // with rotate
        } else {
            this.send(nms, config, id, uuid);
        }

        // Add IDs to queue
        for (int i : id) {
            config.fireworkEntities.add(i);
        }
    }

    private void sendRotate(NMSProvider nms, FireworkStartupConfig config, int[] id, UUID[] uuid) {
        for (Player p : config.players) {
            Location tan = config.startupLocation;
            double tx = tan.getX();
            double ty = tan.getY();
            double tz = tan.getZ();

            Location loc = p.getLocation();
            double cx = loc.getX();
            double cz = loc.getZ();
            double nt = FireworkUtil.normT(cx, cz, tx, tz);

            int totalEnt = 0;
            for (String line : this.lines) {
                char[] chars = line.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    if (chars[i] == '0') {  // skip empty chars
                        continue;
                    }

                    Location fwl = FireworkUtil.calculatePoint(i+1,
                            loc.getWorld(),
                            cx, cz,
                            tx, ty, tz,
                            nt,
                            chars.length * this.size, chars.length);

                    // send
                    NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id[totalEnt], uuid[totalEnt], fwl);
                    NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id[totalEnt], this.preset);
                    nms.sendAddEntity(p,
                            add,
                            data);

                    totalEnt ++;
                }
                ty -= this.size;
            }
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
                NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id[totalEnt], this.preset);
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
