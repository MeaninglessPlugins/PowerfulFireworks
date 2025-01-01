package org.eu.pcraft.powerfulfireworks.nms.v1_21_4;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.Wrapper;

import java.util.UUID;

@AllArgsConstructor
public class NMSAddEntityPacketImpl implements NMSAddEntityPacket, Wrapper<ClientboundAddEntityPacket> {
    private final ClientboundAddEntityPacket packet;

    @Override
    public int getId() {
        return this.packet.getId();
    }

    @Override
    public UUID getUuid() {
        return this.packet.getUUID();
    }

    @Override
    public double x() {
        return this.packet.getX();
    }

    @Override
    public double y() {
        return this.packet.getY();
    }

    @Override
    public double z() {
        return this.packet.getZ();
    }

    @Override
    public float pitch() {
        return this.packet.getXRot();
    }

    @Override
    public float yaw() {
        return this.packet.getYRot();
    }

    @Override
    public ClientboundAddEntityPacket unwrap() {
        return this.packet;
    }
}
