package org.eu.pcraft.powerfulfireworks.nms.v1_20_4;

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
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class NMSProviderImpl implements NMSProvider {
    private final VarHandle vhEntityEventEntityId;

    public NMSProviderImpl() {
        MethodHandles.Lookup mh = MethodHandles.lookup();

        try {
            Field fd = ClientboundEntityEventPacket.class.getDeclaredField("a");    // entityId
            fd.setAccessible(true);
            this.vhEntityEventEntityId = MethodHandles.privateLookupIn(ClientboundEntityEventPacket.class, mh).unreflectVarHandle(fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get entity event accessor", t);
        }
    }

    @Override
    public String getVersion() {
        return "1.20.4";
    }

    @Override
    public NMSPlayer getPlayer(Player player) {
        return new NMSPlayerImpl((CraftPlayer) player);
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
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);

        return new NMSEntityDataPacketImpl(new ClientboundSetEntityDataPacket(id, List.of(new SynchedEntityData.DataItem<>(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, nms).value())));
    }

    @Override
    public int allocateEntityId() {
        return Entity.nextEntityId();
    }

    int getEntityEventEntityId(ClientboundEntityEventPacket packet) {
        return (int) this.vhEntityEventEntityId.get(packet);
    }
}
