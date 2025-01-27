package org.eu.pcraft.powerfulfireworks.utils.sender;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.LocationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.eu.pcraft.powerfulfireworks.utils.FireworkUtil.getRandomFireworkItem;
import static org.eu.pcraft.powerfulfireworks.utils.LocationUtil.*;

@Getter
    public class TextFirework implements FireworkSender{
        List<Player> playerList;
        BitmapFont font;
        String text;
        int gap;
        double size;
        BlockFace face;

        protected String[] charBitmap;
        int[] id;
        UUID[] uuid;

        public TextFirework(BitmapFont font, String text, int gap, double size) {
            this.font = font;
            this.text = text;
            this.gap = gap;
            this.size = size;
            this.face = null;
        }
        public TextFirework(BitmapFont font, String text, int gap, double size, BlockFace face){
            this(font, text, gap, size);
            this.face = face;
        }

        @Override
        public void execute(int flyTime, ItemStack stack, Location fwLoc, List<Player> playerList) {
            int totalEnt = 0;
            charBitmap = font.fromString(text, gap).getChars();
            List<Location> list = new ArrayList<>();
            int length = charBitmap[0].length();

            int totalFw = 0;
            for (String line : charBitmap) {
                totalFw += line.length() - line.replace("1", "").length();
            }
            id = new int[totalFw + 1];
            uuid = new UUID[totalFw + 1];
            FireworkSender.calculateEntityIds(totalFw, id, uuid);
            if(face != null){
                int xOff = face.getModX();
                int zOff = face.getModZ();
                for (int i = 0; i < length; i++) {
                    list.add(new Location(fwLoc.getWorld(),
                            fwLoc.getX() - ((xOff * size * length) / 2.0) + i * size * xOff,
                            fwLoc.getY() + size,
                            fwLoc.getZ() - ((zOff * size * length) / 2.0) + i * size * zOff
                    ));
                }
            }
            for (Player p : playerList) {
                if(face == null){
                    Location pLoc = p.getLocation();
                    list = calculatePoint(p.getWorld(),
                            pLoc.x(), pLoc.z(),
                            fwLoc.x(), fwLoc.y() + size, fwLoc.z(),
                            length * size, length);
                }
                for (String line : charBitmap) {
                    for (int i = 0; i < length; i++) {
                        list.get(i).subtract(0, size, 0);
                        if (line.charAt(i) == '0') {  // skip empty chars
                            continue;
                        }
                        // send
                        NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id[totalEnt], uuid[totalEnt], list.get(i));
                        NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id[totalEnt], stack);
                        nms.sendAddEntity(p, add, data);
                        totalEnt++;
                    }
                }
            }
            // make an explosion task
            BukkitRunnable fireworkExplosionTask = new BukkitRunnable() {
                @Override
                public void run() {
                    FireworkUtil.broadcastFireworkExplosion(playerList, id);
                }
            };
            fireworkExplosionTask.runTaskLater(PowerfulFireworks.getInstance(), flyTime);


        }
    }