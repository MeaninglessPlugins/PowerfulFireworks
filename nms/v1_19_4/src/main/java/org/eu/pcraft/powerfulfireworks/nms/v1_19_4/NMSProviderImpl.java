package org.eu.pcraft.powerfulfireworks.nms.v1_19_4;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.*;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSAddEntityPacketImpl;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSEntityDataPacketImpl;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSEntityEventPacketImpl;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSRemoveEntityPacketImpl;

import java.util.List;
import java.util.UUID;

public class NMSProviderImpl extends org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSProviderImpl {
    @Override
    public String getVersion() {
        return "1.19.4";
    }

    @Override
    public NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location) {
        return new NMSAddEntityPacketImpl(new ClientboundAddEntityPacket(id, uuid, location.x(), location.y(), location.z(), location.getPitch(), location.getYaw(), EntityType.FIREWORK_ROCKET, 0, Vec3.ZERO, 0));
    }

    @Override
    public NMSEntityEventPacket createEntityEvent(int entity, byte event) {
        FriendlyByteBuf fbb = new FriendlyByteBuf(Unpooled.buffer());
        fbb.writeInt(entity);
        fbb.writeByte(event);
        return new NMSEntityEventPacketImpl(this, new ClientboundEntityEventPacket(fbb));
    }

    @Override
    public NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item) {
        return new NMSEntityDataPacketImpl(new ClientboundSetEntityDataPacket(id, List.of(
                new SynchedEntityData.DataValue<>(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM.getId(), FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM.getSerializer(), CraftItemStack.asNMSCopy(item))
        )));
    }

    @Override
    public void sendAddEntity(Player player, NMSAddEntityPacket addEntityPacket, NMSEntityDataPacket dataPacket) {
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(((NMSAddEntityPacketImpl) addEntityPacket).unwrap());
        connection.send(((NMSEntityDataPacketImpl) dataPacket).unwrap());
    }

    @Override
    public void sendEntityEvent(Player player, NMSEntityEventPacket packet) {
        ((CraftPlayer) player).getHandle().connection.send(((NMSEntityEventPacketImpl) packet).unwrap());
    }

    @Override
    public void sendRemoveEntity(Player player, NMSRemoveEntityPacket removeEntityPacket) {
        ((CraftPlayer) player).getHandle().connection.send(((NMSRemoveEntityPacketImpl) removeEntityPacket).unwrap());
    }
}
