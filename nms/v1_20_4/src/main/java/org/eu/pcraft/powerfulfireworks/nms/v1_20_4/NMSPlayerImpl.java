package org.eu.pcraft.powerfulfireworks.nms.v1_20_4;

import lombok.AllArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSPlayer;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class NMSPlayerImpl implements NMSPlayer {
    private final CraftPlayer player;

    @Override
    public void sendFakeFirework(int id, UUID uuid, Location location, NMSEntityDataPacket packet) {
        this.player.getHandle().connection.send(new ClientboundBundlePacket(List.of(
                new ClientboundAddEntityPacket(
                        id, uuid,
                        location.x(), location.y(), location.z(),
                        location.getPitch(), location.getYaw(),
                        EntityType.FIREWORK_ROCKET, 0,
                        Vec3.ZERO,
                        0
                ),
                ((NMSEntityDataPacketImpl) packet).unwrap()
        )));
    }

    @Override
    public void sendEntityEvent(NMSEntityEventPacket packet) {
        this.player.getHandle().connection.send(((NMSEntityEventPacketImpl) packet).unwrap());
    }

    @Override
    public void sendRemoveEntity(int... ids) {
        this.player.getHandle().connection.send(new ClientboundRemoveEntitiesPacket(ids));
    }

    @Override
    public Player unwrap() {
        return this.player;
    }
}
