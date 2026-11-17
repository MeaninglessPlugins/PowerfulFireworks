package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FireworkUtil {

    /** 创建一个假烟花实体之后留下的信息：实体 id 与创建时写在同步数据里的静音状态。 */
    public record CreatedFirework(int id, boolean silent) {
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
     * @param launchChance 发射声播放概率（0-1），每个烟花独立掷一次
     * @return 实体 id 与创建时的静音状态
     */
    public static CreatedFirework broadcastFireworkCreate(Collection<Player> targets, ItemStack item, Location location, double launchChance) {
        return broadcastFireworkCreate(targets, item, location, launchChance, 0.0, 0.0, 0.0);
    }

    /**
     * Broadcast firework add and data to players
     *
     * @param targets players
     * @param item firework item
     * @param location location
     * @param launchChance 发射声播放概率（0-1），每个烟花独立掷一次
     * @param vx 水平初速度（格/tick）
     * @param vy 竖直初速度（格/tick）
     * @param vz 水平初速度（格/tick）
     * @return 实体 id 与创建时的静音状态
     */
    public static CreatedFirework broadcastFireworkCreate(Collection<Player> targets, ItemStack item, Location location,
                                                          double launchChance, double vx, double vy, double vz) {
        if (item.getType() != Material.FIREWORK_ROCKET)
            throw new IllegalArgumentException("Item is not a firework rocket");
        if (!item.hasItemMeta())
            throw new IllegalArgumentException("Item has no ItemMeta");
        if (targets.isEmpty())
            throw new IllegalArgumentException("No target specified");

        NMSProvider provider = PowerfulFireworks.getInstance().getNms();

        // prepare packets
        final int id = provider.allocateEntityId();
        NMSAddEntityPacket add = provider.createAddFireworkEntityPacket(id, UUID.randomUUID(), location, vx, vy, vz);
        boolean launchSilent = ThreadLocalRandom.current().nextDouble() >= launchChance;
        NMSEntityDataPacket data = provider.createFireworkEntityDataPacket(id, item, launchSilent);

        for (Player target : targets) {
            provider.sendAddEntity(target, add, data);
        }

        return new CreatedFirework(id, launchSilent);
    }

    /**
     * broadcast multiple firework explosions to players
     * @param targets target players
     * @param explosionChance 爆炸声播放概率（0-1），每个烟花独立掷一次
     * @param firework target firework entities
     */
    public static void broadcastFireworkExplosion(Collection<Player> targets, double explosionChance, int... firework) {
        broadcastFireworkExplosion(targets, explosionChance, null, firework);
    }

    /**
     * broadcast multiple firework explosions to players
     *
     * @param createSilent 各实体创建时写入的静音状态，与 {@code firework} 一一对应；
     *                     引爆时掷出的结果与之相同时不必再发一个数据包
     */
    public static void broadcastFireworkExplosion(Collection<Player> targets, double explosionChance,
                                                  boolean[] createSilent, int... firework) {
        if (firework.length == 0)
            return;
        if (createSilent != null && createSilent.length != firework.length)
            throw new IllegalArgumentException("silent array length mismatch: "
                    + createSilent.length + " vs " + firework.length);

        NMSProvider provider = PowerfulFireworks.getInstance().getNms();
        ThreadLocalRandom rd = ThreadLocalRandom.current();

        if (firework.length == 1) {
            // prepare event packets
            NMSEntityEventPacket event = provider.createEntityEvent(firework[0], (byte) 17);
            NMSRemoveEntityPacket remove = provider.createRemoveEntityPacket(firework);
            // 爆炸声取决于引爆时的静音标记，发射声早就播完了，改它只影响这一下；
            // 和创建时一样就不必再发一遍
            boolean explosionSilent = rd.nextDouble() >= explosionChance;
            NMSEntityDataPacket silent = createSilent == null || createSilent[0] != explosionSilent
                    ? provider.createSilentEntityDataPacket(firework[0], explosionSilent)
                    : null;
            for (Player target : targets) {
                if (silent != null)
                    provider.sendEntityData(target, silent);
                provider.sendEntityEvent(target, event);
                provider.sendRemoveEntity(target, remove);
            }
        } else {
            // multiple in one time
            List<NMSEntityDataPacket> silentPackets = new ArrayList<>();
            List<NMSEntityEventPacket> events = new ArrayList<>();
            for (int i = 0; i < firework.length; i++) {
                boolean explosionSilent = rd.nextDouble() >= explosionChance;
                NMSEntityDataPacket silent = createSilent == null || createSilent[i] != explosionSilent
                        ? provider.createSilentEntityDataPacket(firework[i], explosionSilent)
                        : null;
                silentPackets.add(silent);
                events.add(provider.createEntityEvent(firework[i], (byte) 17));
            }
            NMSRemoveEntityPacket remove = provider.createRemoveEntityPacket(firework);
            for (Player target : targets) {
                for (int i = 0; i < events.size(); i++) {
                    NMSEntityDataPacket silent = silentPackets.get(i);
                    if (silent != null)
                        provider.sendEntityData(target, silent);
                    provider.sendEntityEvent(target, events.get(i));
                }
                provider.sendRemoveEntity(target, remove);
            }
        }
    }

}