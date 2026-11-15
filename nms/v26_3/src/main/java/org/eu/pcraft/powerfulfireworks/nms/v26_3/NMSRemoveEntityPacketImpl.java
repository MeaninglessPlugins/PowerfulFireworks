package org.eu.pcraft.powerfulfireworks.nms.v26_3;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSRemoveEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.Wrapper;

@AllArgsConstructor
public class NMSRemoveEntityPacketImpl implements NMSRemoveEntityPacket, Wrapper<ClientboundRemoveEntitiesPacket> {
    private final ClientboundRemoveEntitiesPacket packet;

    @Override
    public int[] id() {
        // 26.3 turned this packet into a record; the getter is entityIds() now.
        return this.packet.entityIds().toIntArray();
    }

    @Override
    public ClientboundRemoveEntitiesPacket unwrap() {
        return this.packet;
    }
}
