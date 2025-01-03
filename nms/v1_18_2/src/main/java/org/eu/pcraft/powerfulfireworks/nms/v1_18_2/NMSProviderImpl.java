package org.eu.pcraft.powerfulfireworks.nms.v1_18_2;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class NMSProviderImpl implements NMSProvider {
    final VarHandle vhEntityEventEntityId;

    public NMSProviderImpl() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            Field fd = ClientboundEntityEventPacket.class.getDeclaredField("a");    // entityId
            fd.setAccessible(true);
            vhEntityEventEntityId = MethodHandles.privateLookupIn(ClientboundEntityEventPacket.class, lookup).unreflectVarHandle(fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get entity event accessor");
        }
    }

    @Override
    public String getVersion() {
        return "1.18.2";
    }

    @Override
    public NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location) {
        return new NMSAddEntityPacketImpl(new ClientboundAddEntityPacket(
                id, uuid,
                location.getX(), location.getY(), location.getZ(),
                location.getPitch(), location.getYaw(),
                EntityType.FIREWORK_ROCKET, 0,
                Vec3.ZERO
        ));
    }

    @Override
    public NMSEntityEventPacket createEntityEvent(int entity, byte event) {
        FriendlyByteBuf fbb = new FriendlyByteBuf(Unpooled.buffer());
        fbb.writeInt(entity);
        fbb.writeByte(event);
        return new NMSEntityEventPacketImpl(this, new ClientboundEntityEventPacket(fbb));
    }

    @Override
    public NMSEntityDataPacketImpl createFireworkEntityDataPacket(int id, ItemStack item) {
        FriendlyByteBuf fbb = new FriendlyByteBuf(Unpooled.buffer());
        fbb.writeVarInt(id);
//        SynchedEntityData.pack(List.of(new SynchedEntityData.DataItem<>(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, CraftItemStack.asNMSCopy(item))), fbb);
        EntityDataAccessor<net.minecraft.world.item.ItemStack> access = FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM;
        // Originally from SynchedEntityData#pack and SynchedEntityData#writeDataItem
        fbb.writeByte(access.getId());
        fbb.writeVarInt(EntityDataSerializers.getSerializedId(EntityDataSerializers.ITEM_STACK));
        EntityDataSerializers.ITEM_STACK.write(fbb, CraftItemStack.asNMSCopy(item));
        fbb.writeByte(255);
        return new NMSEntityDataPacketImpl(new ClientboundSetEntityDataPacket(fbb));
    }

    @Override
    public NMSRemoveEntityPacket createRemoveEntityPacket(int... id) {
        return new NMSRemoveEntityPacketImpl(new ClientboundRemoveEntitiesPacket(id));
    }

    @Override
    public void sendAddEntity(Player player, NMSAddEntityPacket addEntityPacket, NMSEntityDataPacket dataPacket) {
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(((NMSAddEntityPacketImpl) addEntityPacket).packet);
        connection.send(((NMSEntityDataPacketImpl) dataPacket).packet);
    }

    @Override
    public void sendEntityEvent(Player player, NMSEntityEventPacket packet) {
        ((CraftPlayer) player).getHandle().connection.send(((NMSEntityEventPacketImpl) packet).packet);
    }

    @Override
    public void sendRemoveEntity(Player player, NMSRemoveEntityPacket removeEntityPacket) {
        ((CraftPlayer) player).getHandle().connection.send(((NMSRemoveEntityPacketImpl) removeEntityPacket).packet);
    }

    @Override
    public int allocateEntityId() {
        return Entity.nextEntityId();
    }
}
