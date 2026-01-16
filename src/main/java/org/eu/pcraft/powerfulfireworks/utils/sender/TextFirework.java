package org.eu.pcraft.powerfulfireworks.utils.sender;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSAddEntityPacket;
import org.eu.pcraft.powerfulfireworks.nms.common.NMSEntityDataPacket;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.eu.pcraft.powerfulfireworks.utils.LocationUtil.calculatePoint;

@Getter
public class TextFirework implements FireworkSender {

    private final BitmapFont font;
    private final String text;
    private final int gap;
    private final double size;
    private final BlockFace face;

    // 缓存计算结果，避免重复计算
    private String[] bitmapLines;
    private int[] entityIds;
    private UUID[] entityUuids;

    public TextFirework(BitmapFont font, String text, int gap, double size) {
        this(font, text, gap, size, null);
    }

    public TextFirework(BitmapFont font, String text, int gap, double size, BlockFace face) {
        this.font = font;
        this.text = text;
        this.gap = gap;
        this.size = size;
        this.face = face;
    }

    @Override
    public void execute(int flyTime, ItemStack stack, Location originLoc, List<Player> playerList) {
        // 1. 初始化点阵数据
        this.bitmapLines = font.fromString(text, gap).chars();

        // 2. 计算需要生成的实体总数 (烟花总像素点)
        int totalFireworks = calculateTotalPoints(bitmapLines);

        // 3. 生成所有烟花实体的 ID 和 UUID (所有玩家看到的是同一组实体 ID)
        this.entityIds = new int[totalFireworks + 1];
        this.entityUuids = new UUID[totalFireworks + 1];
        FireworkSender.calculateEntityIds(totalFireworks, entityIds, entityUuids);

        // 4. 发送生成包 (根据是否固定朝向分流处理)
        if (face != null) {
            handleFixedDirection(stack, originLoc, playerList);
        } else {
            handleDynamicDirection(stack, originLoc, playerList);
        }

        // 5. 创建爆炸任务 (延迟引爆)
        new BukkitRunnable() {
            @Override
            public void run() {
                FireworkUtil.broadcastFireworkExplosion(playerList, entityIds);
            }
        }.runTaskLater(PowerfulFireworks.getInstance(), flyTime);
    }

    /**
     * 处理固定朝向的烟花
     */
    private void handleFixedDirection(ItemStack stack, Location originLoc, List<Player> recipients) {
        int length = bitmapLines[0].length();
        List<Location> currentLocs = new ArrayList<>();

        int xMod = face.getModX();
        int zMod = face.getModZ();

        // 计算起始 X 和 Z (保持原有的水平居中逻辑)
        double startX = originLoc.getX() - ((xMod * size * length) / 2.0);
        double startZ = originLoc.getZ() - ((zMod * size * length) / 2.0);

        // 初始化基准位置列表
        for (int i = 0; i < length; i++) {
            currentLocs.add(new Location(originLoc.getWorld(),
                    startX + i * size * xMod,
                    originLoc.getY(), // 从脚下开始
                    startZ + i * size * zMod
            ));
        }

        int currentIdIndex = 0;

        for (int lineIndex = bitmapLines.length - 1; lineIndex >= 0; lineIndex--) {
            String line = bitmapLines[lineIndex];

            for (int i = 0; i < length; i++) {
                Location loc = currentLocs.get(i).add(0, size, 0);

                if (line.charAt(i) != '0') {
                    sendPacketToAll(recipients, entityIds[currentIdIndex], entityUuids[currentIdIndex], loc, stack);
                    currentIdIndex++;
                }
            }
        }
    }

    /**
     * 处理动态朝向的烟花 (修改为从下往上生成)
     */
    private void handleDynamicDirection(ItemStack stack, Location originLoc, List<Player> recipients) {
        int length = bitmapLines[0].length();

        for (Player p : recipients) {
            Location pLoc = p.getLocation();

            // 计算基准点
            List<Location> currentLocs = calculatePoint(p.getWorld(),
                    pLoc.getX(), pLoc.getZ(),
                    originLoc.getX(), originLoc.getY(), originLoc.getZ(),
                    length * size, length);

            int currentIdIndex = 0;

            for (int lineIndex = bitmapLines.length - 1; lineIndex >= 0; lineIndex--) {
                String line = bitmapLines[lineIndex];

                for (int i = 0; i < length; i++) {
                    Location loc = currentLocs.get(i).add(0, size, 0);

                    if (line.charAt(i) != '0') {
                        sendSinglePacket(p, entityIds[currentIdIndex], entityUuids[currentIdIndex], loc, stack);
                        currentIdIndex++;
                    }
                }
            }
        }
    }

    // 发送给单个玩家
    private void sendSinglePacket(Player p, int id, UUID uuid, Location loc, ItemStack stack) {
        NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id, uuid, loc);
        NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id, stack);
        nms.sendAddEntity(p, add, data);
    }

    // 广播给玩家
    private void sendPacketToAll(List<Player> players, int id, UUID uuid, Location loc, ItemStack stack) {
        NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id, uuid, loc);
        NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id, stack);
        for (Player p : players) {
            nms.sendAddEntity(p, add, data);
        }
    }

    // 计算有效像素点总数
    private int calculateTotalPoints(String[] lines) {
        int count = 0;
        for (String line : lines) {
            for (char c : line.toCharArray()) {
                if (c != '0') count++;
            }
        }
        return count;
    }
}