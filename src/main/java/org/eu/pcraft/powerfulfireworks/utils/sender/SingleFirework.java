package org.eu.pcraft.powerfulfireworks.utils.sender;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.FlightUtil;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.List;

@Getter
    public class SingleFirework implements FireworkSender{

        private final double launchChance;
        private final double explosionChance;
        /** 竖直初速度（格/tick） */
        private final double velocity;

        public SingleFirework(double launchChance, double explosionChance) {
            this(launchChance, explosionChance, 0.0);
        }

        public SingleFirework(double launchChance, double explosionChance, double velocity) {
            this.launchChance = launchChance;
            this.explosionChance = explosionChance;
            this.velocity = velocity;
        }

        @Override
        public void execute(int flyTime, ItemStack stack, Location fwLoc, List<Player> playerList) {
            FireworkUtil.CreatedFirework created = FireworkUtil.broadcastFireworkCreate(
                    playerList,
                    stack,
                    fwLoc,
                    launchChance, 0.0, velocity, 0.0);
            BukkitRunnable fireworkExplosionTask = new BukkitRunnable() {
                @Override
                public void run() {
                    FireworkUtil.broadcastFireworkExplosion(playerList, explosionChance,
                            new boolean[]{created.silent()}, created.id());
                }
            };
            // 按客户端 64 格渲染范围裁剪飞行时间，避免火箭在引爆前被截断
            fireworkExplosionTask.runTaskLater(PowerfulFireworks.getInstance(),
                    FlightUtil.clampFlyTime(flyTime, fwLoc, playerList, velocity));
        }
    }
