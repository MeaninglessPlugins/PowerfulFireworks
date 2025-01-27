package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LocationUtil {
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
}
