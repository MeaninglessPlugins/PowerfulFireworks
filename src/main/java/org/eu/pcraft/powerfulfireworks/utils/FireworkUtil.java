package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class FireworkUtil {
    public static int getMaxDistance() {
        PowerfulFireworks pl = PowerfulFireworks.getInstance();
        if (pl.getMainConfig().randomFirework.automaticDistance) {
            return pl.getServer().getViewDistance()*16;
        }
        return pl.getMainConfig().randomFirework.distance;
    }

    public static Location getRandomLocation(Location location, int maxDistance) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        double u = random.nextDouble();
        double theta = 2 * Math.PI * random.nextDouble();
        double radius = maxDistance * Math.sqrt(u);

        // 将极坐标转换为笛卡尔坐标
        location.setX(location.x() + radius * Math.cos(theta));
        location.setZ(location.z() + radius * Math.sin(theta));
        location.setY(location.getWorld().getHighestBlockYAt(location) + 1);
        return location;
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
     * Generate a random-firework
     * Modify from plugin: Festival Fireworks
     * @return Firework item stack
     */
    public static ItemStack getRandomFireworkItem() {
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

    public static double normT(double centerX, double centerZ, double tangentX, double tangentZ) {
        double tx = centerZ - tangentZ;
        double tz = (tangentX - centerX);
        return Math.sqrt(tx * tx + tz * tz);
    }

    /**
     * Calculates the coordinates of the i-th point on a line segment that is equally divided into `num` parts.
     * The line segment lies along the tangent of a circle at a given tangent point and is symmetric about the tangent point.
     *
     * @param i              The index of the point to calculate (1-based index, where 1 <= i <= num).
     * @param centerX        Center location X
     * @param centerZ        Center location Z
     * @param tangentX       Tangent location X
     * @param tangentY       Tangent location Y
     * @param tangentZ       Tangent location Z
     * @param normT          {@link #normT(double, double, double, double)}
     * @param t              The total length of the line segment to be divided.
     * @param num            The number of equal parts to divide the line segment into.
     * @return               A Location object representing the coordinates of the i-th point on the line segment.
     *
     * @throws IllegalArgumentException If the number of divisions (num) is less than or equal to 0,
     *                                  or if the index i is out of bounds (i < 1 or i > num).
     */
    public static Location calculatePoint(int i, World world, double centerX, double centerZ, double tangentX, double tangentY, double tangentZ, double normT, double t, int num) {
        if (num <= 0) {
            throw new IllegalArgumentException("Number of divisions (num) must be greater than 0.");
        }
        if (i < 1 || i > num) {
            throw new IllegalArgumentException("Index i must be between 1 and num, inclusive.");
        }

        // Calculate the tangent direction vector components
        double tx = centerZ - tangentZ;
        double tz = (tangentX - centerX);

        if (normT == 0) {
            throw new IllegalArgumentException("The center and tangent point must not be the same location.");
        }

        // Calculate the scaling factor for the i-th point
        double factor = (2 * i - num) / (double) num;

        // Calculate the coordinates of the i-th point
        double x_i = tangentX + (t / 2) * (tx / normT) * factor;
        double z_i = tangentZ + (t / 2) * (tz / normT) * factor;

        // Return the calculated Location
        return new Location(world, x_i, tangentY, z_i);
    }
}