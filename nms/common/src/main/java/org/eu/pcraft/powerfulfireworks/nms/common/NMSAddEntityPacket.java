package org.eu.pcraft.powerfulfireworks.nms.common;

import org.bukkit.Location;

import java.util.UUID;

public interface NMSAddEntityPacket {
    int getId();
    UUID getUuid();

    double x();
    double y();
    double z();

    float pitch();
    float yaw();
}
