package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

import java.util.ArrayList;
import java.util.List;

import static org.eu.pcraft.powerfulfireworks.utils.FireworkUtil.get2dSqrDistance;

@Getter
@Setter
//@RequiredArgsConstructor
public class FireworkStartupConfig {
    public FireworkStartupConfig(Location location, List<Player> playerList) {
        startupLocation = location;
        originalPlayers = playerList;
    }
    FireworkScheduler scheduler;

    final PowerfulFireworks plugin = PowerfulFireworks.getInstance();

    final Location startupLocation;
    final List<Player> originalPlayers;
    public void updatePlayers() {
        players.clear();
        for(Player player : originalPlayers){
            if(get2dSqrDistance(startupLocation, player.getLocation()) > (long) Bukkit.getViewDistance() <<1){
                continue;
            }
            players.add(player);
        }
    }
    List<Player> players = new ArrayList<>();
}
