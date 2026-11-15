package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LocationUtil {
    /** 随机取点最多重试几次，全都落在未加载区块时退回起点。 */
    private static final int RANDOM_LOCATION_ATTEMPTS = 8;

    public static Location getRandomLocation(Location location, int maxDistance) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final World world = location.getWorld();
        final double centerX = location.getX();
        final double centerZ = location.getZ();

        for (int attempt = 0; attempt < RANDOM_LOCATION_ATTEMPTS; attempt++) {
            double u = random.nextDouble();
            double theta = 2 * Math.PI * random.nextDouble();
            double radius = maxDistance * Math.sqrt(u);

            // 将极坐标转换为笛卡尔坐标
            double x = centerX + radius * Math.cos(theta);
            double z = centerZ + radius * Math.sin(theta);

            // 只往已加载的区块里放：未加载时读高度会触发区块加载，主线程上会卡服
            if (!world.isChunkLoaded(Location.locToBlock(x) >> 4, Location.locToBlock(z) >> 4))
                continue;

            location.setX(x);
            location.setZ(z);
            location.setY(world.getHighestBlockYAt(location) + 1);
            return location;
        }

        // 兜底：退回起点，玩家所在的区块一定是加载的
        location.setY(world.getHighestBlockYAt(location) + 1);
        return location;
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
     * @param centerX        Center location X
     * @param centerZ        Center location Z
     * @param tangentX       Tangent location X
     * @param tangentY       Tangent location Y
     * @param tangentZ       Tangent location Z
     * @param t              The total length of the line segment to be divided.
     * @param num            The number of equal parts to divide the line segment into.
     * @return               A Location object representing the coordinates of the i-th point on the line segment.
     *
     * @throws IllegalArgumentException If the number of divisions (num) is less than or equal to 0,
     *                                  or if the index i is out of bounds (i < 1 or i > num).
     */
    public static List<Location> calculatePoint(World world, double centerX, double centerZ, double tangentX, double tangentY, double tangentZ, double t, int num) {

        if (num <= 0) {
            throw new IllegalArgumentException("Number of divisions (num) must be greater than 0.");
        }

        // Calculate the tangent direction vector components
        double tx = centerZ - tangentZ;
        double tz = (tangentX - centerX);

        double normT = normT(centerX, centerZ, tangentX, tangentZ);
        if (normT == 0) {
            throw new IllegalArgumentException("The center and tangent point must not be the same location.");
        }

        List<Location> ret = new ArrayList<>();

        for(int i=0; i < num; i++){
            // Calculate the scaling factor for the i-th point
            double factor = (2 * i - num) / (double) num;

            // Calculate the coordinates of the i-th point
            double x_i = tangentX + (t / 2) * (tx / normT) * factor;
            double z_i = tangentZ + (t / 2) * (tz / normT) * factor;
            ret.add(new Location(world, x_i, tangentY, z_i));
        }

        // Return the calculated Location List
        return ret;
    }

    public static int getMaxDistance() {
        PowerfulFireworks pl = PowerfulFireworks.getInstance();
        if (pl.getMainConfig().randomFirework.automaticDistance) {
            return pl.getServer().getViewDistance()*16;
        }
        return pl.getMainConfig().randomFirework.distance;
    }

    /**
    * Get squared horizontal distance between the two locations
    * @param from the first location
    * @param to the second location
    * @return dx*dx + dz*dz
    */
    public static long get2dSqrDistance(Location from, Location to){
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (long) (dx * dx + dz * dz);
    }
}
