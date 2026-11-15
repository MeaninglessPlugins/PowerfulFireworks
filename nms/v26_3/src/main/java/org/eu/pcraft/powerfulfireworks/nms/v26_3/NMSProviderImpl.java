package org.eu.pcraft.powerfulfireworks.nms.v26_3;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSRemoveEntityPacket;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class NMSProviderImpl implements NMSProvider {
    private static final String VERSION = "26.3";

    private final MethodHandle mhEntityEventConstructor;
    private final VarHandle vhEntityEventEntityId;

    public NMSProviderImpl() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        // ClientboundEntityEventPacket 的公开构造器要一个实体实例，我们只有实体 id；
        // 于是走私有的 (FriendlyByteBuf) 构造器：它的读法与写包顺序都是 writeInt(id) + writeByte(event)。
        try {
            Constructor<ClientboundEntityEventPacket> constructor =
                    ClientboundEntityEventPacket.class.getDeclaredConstructor(FriendlyByteBuf.class);
            constructor.setAccessible(true);
            this.mhEntityEventConstructor = lookup.unreflectConstructor(constructor);
        } catch (Throwable t) {
            throw new RuntimeException(VERSION + ": ClientboundEntityEventPacket 的 (FriendlyByteBuf) 构造器不可用", t);
        }
        // 该包没有 getEntityId()，只能读私有字段；字段名一变这里会立刻失败，属于有意为之的 fail fast。
        try {
            Field field = ClientboundEntityEventPacket.class.getDeclaredField("entityId");
            field.setAccessible(true);
            this.vhEntityEventEntityId = MethodHandles.privateLookupIn(ClientboundEntityEventPacket.class, lookup)
                    .unreflectVarHandle(field);
        } catch (Throwable t) {
            throw new RuntimeException(VERSION + ": ClientboundEntityEventPacket.entityId 字段不可用", t);
        }
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location) {
        // 26.x 把 EntityType 上的静态实例拆到了 EntityTypes。
        ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(
                id, uuid, location.x(), location.y(), location.z(), location.getPitch(), location.getYaw(),
                EntityTypes.FIREWORK_ROCKET, 0, Vec3.ZERO, 0.0);
        return new NMSAddEntityPacketImpl(packet);
    }

    @Override
    public NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);
        return new NMSEntityDataPacketImpl(new ClientboundSetEntityDataPacket(id, List.of(
                SynchedEntityData.DataValue.create(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, nms))));
    }

    @Override
    public NMSRemoveEntityPacket createRemoveEntityPacket(int... ids) {
        return new NMSRemoveEntityPacketImpl(new ClientboundRemoveEntitiesPacket(ids));
    }

    @Override
    public void sendAddEntity(Player player, NMSAddEntityPacket addEntityPacket, NMSEntityDataPacket dataPacket) {
        ServerGamePacketListenerImpl connection = connection(player);
        connection.send(((NMSAddEntityPacketImpl) addEntityPacket).unwrap());
        connection.send(((NMSEntityDataPacketImpl) dataPacket).unwrap());
    }

    @Override
    public void sendEntityEvent(Player player, NMSEntityEventPacket packet) {
        connection(player).send(((NMSEntityEventPacketImpl) packet).unwrap());
    }

    @Override
    public void sendRemoveEntity(Player player, NMSRemoveEntityPacket removeEntityPacket) {
        connection(player).send(((NMSRemoveEntityPacketImpl) removeEntityPacket).unwrap());
    }

    /** 分配 id 要查区块里的实体表，所以只能在主线程调用。 */
    @Override
    public int allocateEntityId() {
        // Entity.nextEntityId() 在 26.x 移到了 ServerLevel。
        return MinecraftServer.getServer().overworld().getNextEntityId();
    }

    @Override
    public NMSEntityEventPacket createEntityEvent(int entity, byte event) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity);
        buf.writeByte(event);
        try {
            return new NMSEntityEventPacketImpl(this,
                    (ClientboundEntityEventPacket) this.mhEntityEventConstructor.invoke(buf));
        } catch (Throwable t) {
            throw new RuntimeException(VERSION + ": 构造 ClientboundEntityEventPacket 失败", t);
        }
    }

    int getEntityEventEntityId(ClientboundEntityEventPacket packet) {
        return (int) vhEntityEventEntityId.get(packet);
    }

    private static ServerGamePacketListenerImpl connection(Player player) {
        return ((CraftPlayer) player).getHandle().connection;
    }
}
