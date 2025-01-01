package org.eu.pcraft.powerfulfireworks.nms.v1_20_2;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityEventPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSRemoveEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSAddEntityPacketImpl;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSEntityDataPacketImpl;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSEntityEventPacketImpl;
import org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSRemoveEntityPacketImpl;

import java.util.List;
import java.util.UUID;

public class NMSProviderImpl extends org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSProviderImpl {
    @Override
    public String getVersion() {
        return "1.20.2";
    }

    @Override
    public NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location) {
        return new NMSAddEntityPacketImpl(new ClientboundAddEntityPacket(id, uuid, location.x(), location.y(), location.z(), location.getPitch(), location.getYaw(), EntityType.FIREWORK_ROCKET, 0, Vec3.ZERO, 0));
    }

    @Override
    public NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);

        return new NMSEntityDataPacketImpl(new ClientboundSetEntityDataPacket(id, List.of(new SynchedEntityData.DataItem<>(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, nms).value())));
    }

    @Override
    public void sendAddEntity(Player player, NMSAddEntityPacket addEntityPacket, NMSEntityDataPacket dataPacket) {
        ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
        conn.send(((NMSAddEntityPacketImpl) addEntityPacket).unwrap());
        conn.send(((NMSEntityDataPacketImpl) dataPacket).unwrap());
    }

    @Override
    public void sendEntityEvent(Player player, NMSEntityEventPacket packet) {
        ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
        conn.send(((NMSEntityEventPacketImpl) packet).unwrap());
    }

    @Override
    public void sendRemoveEntity(Player player, NMSRemoveEntityPacket removeEntityPacket) {
        ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
        conn.send(((NMSRemoveEntityPacketImpl) removeEntityPacket).unwrap());
    }
}
