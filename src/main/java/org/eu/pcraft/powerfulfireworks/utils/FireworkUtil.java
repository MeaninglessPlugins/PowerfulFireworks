package org.eu.pcraft.powerfulfireworks.utils;

import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class FireworkUtil {
    Random r = new Random();
    final PowerfulFireworks pl = PowerfulFireworks.getInstance();
    public Location getRandomLocation(Player p){
        int maxDistance=pl.getServer().getViewDistance()*16;
        Location location=p.getLocation();
        location.setX(location.x()+r.nextDouble()*maxDistance);
        location.setZ(location.y()+r.nextDouble()*maxDistance);
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
}