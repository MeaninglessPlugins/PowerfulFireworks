package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.ArrayList;
import java.util.List;

import static org.eu.pcraft.powerfulfireworks.utils.LocationUtil.get2dSqrDistance;

@Getter
@Setter
//@RequiredArgsConstructor
public class FireworkStartupConfig {
    public FireworkStartupConfig(Location location, List<Player> playerList) {
        startupLocation = location;
        originalPlayers = playerList;
    }
    /** 本场演出这一 tick 还剩多少节点额度，引用型节点共享同一份。 */
    FireworkScheduler.NodeBudget nodeBudget;

    final PowerfulFireworks plugin = PowerfulFireworks.getInstance();

    final Location startupLocation;
    final List<Player> originalPlayers;
    public void updatePlayers() {
        players.clear();
        long radius = (long) Bukkit.getViewDistance() * 16;
        long maxSqrDistance = radius * radius;
        for(Player player : originalPlayers){
            if(get2dSqrDistance(startupLocation, player.getLocation()) > maxSqrDistance){
                continue;
            }
            players.add(player);
        }
    }
    List<Player> players = new ArrayList<>();
}
