package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.util.*;

public final class FireworkUtil {
    Random r = new Random();
    final PowerfulFireworks pl = PowerfulFireworks.getInstance();
    public Location getRandomLocation(Player p){
        int maxDistance=pl.getServer().getViewDistance()*16;
        Location location=p.getLocation();
        location.setX(location.x()+(r.nextDouble()*2-1)*maxDistance);
        location.setZ(location.z()+(r.nextDouble()*2-1)*maxDistance);
        int minY=location.getWorld().getHighestBlockYAt(location);
        location.setY(minY+10+r.nextDouble()*400);
        return location;
    }
    public ItemStack getRandomFireworkItem(){
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
        FireworkEffect.Type[] type = FireworkEffect.Type.values();
        fireworkBuilder.with(type[r.nextInt(type.length)]);
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