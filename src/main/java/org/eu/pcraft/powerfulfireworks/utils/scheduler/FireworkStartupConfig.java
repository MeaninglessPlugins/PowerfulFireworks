package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Location;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;

@Getter
@Setter
@RequiredArgsConstructor
public class FireworkStartupConfig {
    FireworkScheduler scheduler;

    final PowerfulFireworks plugin = PowerfulFireworks.getInstance();

    final Location startupLocation;
}
