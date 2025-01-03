package org.eu.pcraft.powerfulfireworks.nms.v1_18_2;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;

@AllArgsConstructor
public class NMSEntityEventPacketImpl implements NMSEntityEventPacket {
    final NMSProviderImpl provider;
    final ClientboundEntityEventPacket packet;

    @Override
    public int getEntity() {
        return (int) this.provider.vhEntityEventEntityId.get(packet);
    }

    @Override
    public byte getEvent() {
        return this.packet.getEventId();
    }
}
