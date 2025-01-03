package org.eu.pcraft.powerfulfireworks.nms.v1_18_2;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSRemoveEntityPacket;

@AllArgsConstructor
public class NMSRemoveEntityPacketImpl implements NMSRemoveEntityPacket {
    final ClientboundRemoveEntitiesPacket packet;

    @Override
    public int[] id() {
        return this.packet.getEntityIds().toArray(new int[0]);
    }
}
