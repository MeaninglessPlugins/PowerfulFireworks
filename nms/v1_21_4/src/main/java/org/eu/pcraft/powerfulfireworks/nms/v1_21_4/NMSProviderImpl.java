package org.eu.pcraft.powerfulfireworks.nms.v1_21_4;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class NMSProviderImpl implements NMSProvider {
    private final MethodHandle mhEntityEventConstructor;
    private final VarHandle vhEntityEventEntityId;

    public NMSProviderImpl() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            Constructor<ClientboundEntityEventPacket> m = ClientboundEntityEventPacket.class.getDeclaredConstructor(FriendlyByteBuf.class);
            m.setAccessible(true);
            this.mhEntityEventConstructor = lookup.unreflectConstructor(m);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get entity event constructor", t);
        }
        try {
            Field f = ClientboundEntityEventPacket.class.getDeclaredField("entityId");
            f.setAccessible(true);
            this.vhEntityEventEntityId = MethodHandles.privateLookupIn(ClientboundEntityEventPacket.class, lookup).unreflectVarHandle(f);
        } catch (Throwable t) {
            throw new RuntimeException("Failed get entity event accessor", t);
        }
    }

    @Override
    public String getVersion() {
        return "1.21.4";
    }

    @Override
    public NMSPlayer getPlayer(Player player) {
        return new NMSPlayerImpl(player);
    }

    @Override
    public NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location) {
        return new NMSAddEntityPacketImpl(new ClientboundAddEntityPacket(id, uuid, location.x(), location.y(), location.z(), location.getPitch(), location.getYaw(), EntityType.FIREWORK_ROCKET, 0, Vec3.ZERO, 0));
    }

    public NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);
        return new NMSEntityDataPacketImpl(new ClientboundSetEntityDataPacket(id, List.of(SynchedEntityData.DataValue.create(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, nms))));
    }

    @Override
    public int allocateEntityId() {
        return Entity.nextEntityId();
    }

    @Override
    public NMSEntityEventPacket createEntityEvent(int entity, byte event) {
        // Create packet with buffer
        FriendlyByteBuf fbb = new FriendlyByteBuf(Unpooled.buffer());
        fbb.writeInt(entity);
        fbb.writeByte(event);
        try {
            return new NMSEntityEventPacketImpl(this, (ClientboundEntityEventPacket) this.mhEntityEventConstructor.invoke(fbb));
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create ClientboundEntityEventPacket", e);
        }
    }

    int getEntityEventEntityId(ClientboundEntityEventPacket packet) {
        return (int) vhEntityEventEntityId.get(packet);
    }
}
