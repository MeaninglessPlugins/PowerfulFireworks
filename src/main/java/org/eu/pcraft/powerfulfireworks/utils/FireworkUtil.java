package org.eu.pcraft.powerfulfireworks.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FireworkUtil {
    public static int getMaxDistance() {
        PowerfulFireworks pl = PowerfulFireworks.getInstance();
        if (pl.getMainConfig().randomFirework.automaticDistance) {
            return pl.getServer().getViewDistance()*16;
        }
        return pl.getMainConfig().randomFirework.distance;
    }

    /**
    * Get sqrDistance between the two location
    * @param from the first location
    * @param to the second location
    * @return distance
    */
    public static long get2dSqrDistance(Location from, Location to){
        long gapX = (long)(to.getX()-from.getX())<<1;
        long gapZ = (long)(to.getZ()-from.getZ())<<1;
        return gapX + gapZ;
    }
    /**
    *
    */
    public static void sendRotateTextFireworks(NMSProvider nms, Player p, Location tan, double size, String[] lines, int[] id, UUID[] uuid){
            double tx = tan.getX();
            double ty = tan.getY();
            double tz = tan.getZ();

            Location loc = p.getLocation();

            double cx = loc.getX();
            double cz = loc.getZ();

            int totalEnt = 0;
            int charLength = lines[0].length();
            List<Location> locationList = LocationUtil.calculatePoint(loc.getWorld(),
                    cx, cz,
                    tx, ty, tz,
                    charLength * size, charLength);
            for (String line : lines) {
                for (int i = 0; i < charLength; i++) {
                    if (line.charAt(i) == '0') {  // skip empty chars
                        continue;
                    }
                    // send
                    NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id[totalEnt], uuid[totalEnt], locationList.get(i).add(0, -i * size, 0));
                    NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id[totalEnt], getRandomFireworkItem(false));
                    nms.sendAddEntity(p,
                            add,
                            data);
                    totalEnt ++;
                }
            }
    }

    /**
     * Generate a random-firework
     * Modify from plugin: Festival Fireworks
     * @return Firework item stack
     */
    public static ItemStack getRandomFireworkItem(boolean isUsedForText) {
        final ThreadLocalRandom r = ThreadLocalRandom.current();
        FireworkEffect.Builder fireworkBuilder = FireworkEffect.builder();

        //随机颜色
        fireworkBuilder.withColor(
                Color.fromRGB(r.nextInt(156) + 100, r.nextInt(156) + 100, r.nextInt(156) + 100),
                Color.fromRGB(r.nextInt(136) + 120, r.nextInt(136) + 120, r.nextInt(136) + 120),
                Color.fromRGB(r.nextInt(116) + 140, r.nextInt(116) + 140, r.nextInt(116) + 140),
                Color.fromRGB(r.nextInt(96) + 160, r.nextInt(96) + 160, r.nextInt(96) + 160)
        );
        fireworkBuilder.withFade(
                Color.fromRGB(r.nextInt(255), r.nextInt(255), r.nextInt(255)),
                Color.fromRGB(r.nextInt(255), r.nextInt(255), r.nextInt(255))
        );

        //随机形状
        if(isUsedForText){
            fireworkBuilder.with(FireworkEffect.Type.BALL);
        }else{
            FireworkEffect.Type[] type = FireworkEffect.Type.values();
            fireworkBuilder.with(type[r.nextInt(type.length)]);
        }

        //随机效果
        int t = r.nextInt(64);
        if (t % 2 == 0) {
            fireworkBuilder.withFlicker();
        }
        if (t % 3 == 0 || t % 13 == 0) {
            fireworkBuilder.withTrail();
        }

        //随机能量
        int power = r.nextInt(3) + 2;

        //存入Itemstack
        ItemStack fireworkItem = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta fireworkMeta = (FireworkMeta) fireworkItem.getItemMeta();
        fireworkMeta.clearEffects();
        fireworkMeta.addEffect(fireworkBuilder.build());
        fireworkMeta.setPower(power);
        fireworkItem.setItemMeta(fireworkMeta);
        return fireworkItem;
    }

    /**
     * Broadcast firework add and data to players
     * @param targets players
     * @param item firework item
     * @param location location
     * @return fake entity ID
     */
    public static int broadcastFireworkCreate(Collection<Player> targets, ItemStack item, Location location) {
        if (item.getType() != Material.FIREWORK_ROCKET)
            throw new IllegalArgumentException("Item is not a firework rocket");
        if (!item.hasItemMeta())
            throw new IllegalArgumentException("Item has no ItemMeta");
        if (targets.isEmpty())
            throw new IllegalArgumentException("No target specified");

        NMSProvider provider = PowerfulFireworks.getInstance().getNms();

        // prepare packets
        final int id = provider.allocateEntityId();
        NMSAddEntityPacket add = provider.createAddFireworkEntityPacket(id, UUID.randomUUID(), location);
        NMSEntityDataPacket data = provider.createFireworkEntityDataPacket(id, item);

        for (Player target : targets) {
            provider.sendAddEntity(target, add, data);
        }

        return id;
    }

    /**
     * broadcast multiple firework explosions to players
     * @param targets target players
     * @param firework target firework entities
     */
    public static void broadcastFireworkExplosion(Collection<Player> targets, int... firework) {
        if (firework.length == 0)
            return;

        NMSProvider provider = PowerfulFireworks.getInstance().getNms();

        if (firework.length == 1) {
            // prepare event packets
            NMSEntityEventPacket event = provider.createEntityEvent(firework[0], (byte) 17);
            NMSRemoveEntityPacket remove = provider.createRemoveEntityPacket(firework);
            for (Player target : targets) {
                provider.sendEntityEvent(target, event);
                provider.sendRemoveEntity(target, remove);
            }
        } else {
            // multiple in one time
            List<NMSEntityEventPacket> events = new ArrayList<>();
            for (int id : firework) {
                NMSEntityEventPacket event = provider.createEntityEvent(id, (byte) 17);
                events.add(event);
            }
            NMSRemoveEntityPacket remove = provider.createRemoveEntityPacket(firework);
            for (Player target : targets) {
                for (NMSEntityEventPacket event : events) {
                    provider.sendEntityEvent(target, event);
                }
                provider.sendRemoveEntity(target, remove);
            }
        }
    }

}