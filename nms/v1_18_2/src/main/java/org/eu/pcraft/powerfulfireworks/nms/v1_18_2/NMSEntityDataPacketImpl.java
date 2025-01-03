package org.eu.pcraft.powerfulfireworks.nms.v1_18_2;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;

@AllArgsConstructor
public class NMSEntityDataPacketImpl implements NMSEntityDataPacket {
    final ClientboundSetEntityDataPacket packet;
}
