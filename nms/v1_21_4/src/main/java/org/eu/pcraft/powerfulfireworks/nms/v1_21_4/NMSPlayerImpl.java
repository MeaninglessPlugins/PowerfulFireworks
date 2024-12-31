package org.eu.pcraft.powerfulfireworks.nms.v1_21_4;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class NMSPlayerImpl implements NMSPlayer {
    private final CraftPlayer player;

    NMSPlayerImpl(Player player) {
        this.player = (CraftPlayer) player;
    }

    @Override
    public Player unwrap() {
        return this.player;
    }

    @Override
    public void sendFakeFirework(int id, UUID uuid, Location location, NMSEntityDataPacket data) {
        List<Packet<? super ClientGamePacketListener>> list = new ArrayList<>();
        list.add(new ClientboundAddEntityPacket(
                id,
                uuid,
                location.x(),
                location.y(),
                location.z(),
                location.getYaw(),
                location.getPitch(),
                EntityType.FIREWORK_ROCKET,
                0,
                Vec3.ZERO,
                0
        ));
        list.add(((NMSEntityDataPacketImpl) data).unwrap());
        this.player.getHandle().connection.sendPacket(new ClientboundBundlePacket(list));
    }

    public void sendEntityData(NMSEntityDataPacket packet) {
        this.player.getHandle().connection.sendPacket(((NMSEntityDataPacketImpl) packet).unwrap());
    }

    public void sendEntityEvent(NMSEntityEventPacket packet) {
        this.player.getHandle().connection.sendPacket(((NMSEntityEventPacketImpl) packet).unwrap());
    }

    public void sendRemoveEntity(int... ids) {
        this.player.getHandle().connection.sendPacket(new ClientboundRemoveEntitiesPacket(ids));
    }
}
