package org.eu.pcraft.powerfulfireworks.nms.v1_21_4;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.Wrapper;

@AllArgsConstructor
public class NMSEntityEventPacketImpl implements NMSEntityEventPacket, Wrapper<ClientboundEntityEventPacket> {
    private final NMSProviderImpl provider;
    private final ClientboundEntityEventPacket packet;

    @Override
    public int getEntity() {
        return this.provider.getEntityEventEntityId(packet);
    }

    @Override
    public byte getEvent() {
        return this.packet.getEventId();
    }

    @Override
    public ClientboundEntityEventPacket unwrap() {
        return this.packet;
    }
}
