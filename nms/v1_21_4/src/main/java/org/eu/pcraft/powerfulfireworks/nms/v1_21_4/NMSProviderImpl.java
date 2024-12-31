package org.eu.pcraft.powerfulfireworks.nms.v1_21_4;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.bukkit.entity.Player;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSPlayer;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
            this.vhEntityEventEntityId = lookup.unreflectVarHandle(f);
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

    public NMSEntityEventPacket createEntityEvent(int entity, byte event) {
        // Create packet with buffer
        FriendlyByteBuf fbb = new FriendlyByteBuf(Unpooled.buffer());
        fbb.writeInt(entity);
        fbb.writeByte(event);
        try {
            return (NMSEntityEventPacket) this.mhEntityEventConstructor.invoke(fbb);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create ClientboundEntityEventpacket", e);
        }
    }

    int getEntityEventEntityId(ClientboundEntityEventPacket packet) {
        return (int) vhEntityEventEntityId.get(packet);
    }
}
