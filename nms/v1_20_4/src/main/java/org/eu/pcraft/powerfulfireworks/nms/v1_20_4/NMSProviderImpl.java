package org.eu.pcraft.powerfulfireworks.nms.v1_20_4;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSPlayer;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;

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

    int getEntityEventEntityId(ClientboundEntityEventPacket packet) {
        return (int) this.vhEntityEventEntityId.get(packet);
    }
}
