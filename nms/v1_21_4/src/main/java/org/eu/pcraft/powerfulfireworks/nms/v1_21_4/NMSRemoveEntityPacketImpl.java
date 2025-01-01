package org.eu.pcraft.powerfulfireworks.nms.v1_21_4;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSRemoveEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.Wrapper;

@AllArgsConstructor
public class NMSRemoveEntityPacketImpl implements NMSRemoveEntityPacket, Wrapper<ClientboundRemoveEntitiesPacket> {
    private final ClientboundRemoveEntitiesPacket packet;

    @Override
    public int[] id() {
        return this.packet.getEntityIds().toIntArray();
    }

    @Override
    public ClientboundRemoveEntitiesPacket unwrap() {
        return this.packet;
    }
}
