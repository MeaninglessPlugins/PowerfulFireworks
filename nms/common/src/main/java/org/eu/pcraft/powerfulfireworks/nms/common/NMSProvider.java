package org.eu.pcraft.powerfulfireworks.nms.common;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface NMSProvider {
    String getVersion();

    NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location);

    /**
     * 创建带初速度的火箭实体包。
     *
     * @param vx 水平初速度（格/tick）
     * @param vy 竖直初速度（格/tick）
     * @param vz 水平初速度（格/tick）
     */
    default NMSAddEntityPacket createAddFireworkEntityPacket(int id, UUID uuid, Location location,
                                                             double vx, double vy, double vz) {
        return createAddFireworkEntityPacket(id, uuid, location);
    }
    NMSEntityEventPacket createEntityEvent(int entity, byte event);
    NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item);

    /**
     * 可指定静音的版本。不支持的实现直接忽略该参数。
     *
     * <p>假烟花在客户端由本地 tick 推进，发射声是客户端自己播放的，服务端只能通过实体的
     * {@code DATA_SILENT} 让它闭嘴。
     */
    default NMSEntityDataPacket createFireworkEntityDataPacket(int id, ItemStack item, boolean silent) {
        return createFireworkEntityDataPacket(id, item);
    }

    /**
     * 只更新实体的静音标记，用来在飞行途中单独控制爆炸声。
     *
     * <p>发射声在实体第一次 tick 时就已经播完，之后改这个标记只会影响爆炸声。
     * 不支持的实现返回 {@code null}，调用方据此跳过。
     */
    default NMSEntityDataPacket createSilentEntityDataPacket(int id, boolean silent) {
        return null;
    }

    /** 单独发送一个实体数据包（不伴随生成包）。 */
    default void sendEntityData(Player player, NMSEntityDataPacket packet) {
    }

    NMSRemoveEntityPacket createRemoveEntityPacket(int... ids);

    void sendAddEntity(Player player, NMSAddEntityPacket addEntityPacket, NMSEntityDataPacket dataPacket);
    void sendEntityEvent(Player player, NMSEntityEventPacket packet);
    void sendRemoveEntity(Player player, NMSRemoveEntityPacket removeEntityPacket);

    int allocateEntityId();
}
