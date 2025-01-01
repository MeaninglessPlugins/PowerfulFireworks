package org.eu.pcraft.powerfulfireworks.nms.v1_20_4;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.Wrapper;

@AllArgsConstructor
public class NMSEntityDataPacketImpl implements NMSEntityDataPacket, Wrapper<ClientboundSetEntityDataPacket> {
    private final ClientboundSetEntityDataPacket packet;

    @Override
    public ClientboundSetEntityDataPacket unwrap() {
        return this.packet;
    }
}
