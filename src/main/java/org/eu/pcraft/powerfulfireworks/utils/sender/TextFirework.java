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
import org.eu.pcraft.powerfulfireworks.utils.FlightUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.eu.pcraft.powerfulfireworks.utils.LocationUtil.calculatePoint;

@Getter
public class TextFirework implements FireworkSender {

    private final BitmapFont font;
    private final String text;
    private final int gap;
    private final double size;
    private final BlockFace face;
    /** 每个火箭发射声的播放概率：0 全部静音，1 全部发声 */
    private final double launchChance;
    /** 每个火箭爆炸声的播放概率：0 全部静音，1 全部发声 */
    private final double explosionChance;

    public TextFirework(BitmapFont font, String text, int gap, double size,
                        double launchChance, double explosionChance) {
        this(font, text, gap, size, null, launchChance, explosionChance);
    }

    public TextFirework(BitmapFont font, String text, int gap, double size, BlockFace face,
                        double launchChance, double explosionChance) {
        this.font = font;
        this.text = text;
        this.gap = gap;
        this.size = size;
        this.face = face;
        this.launchChance = launchChance;
        this.explosionChance = explosionChance;
    }

    @Override
    public void execute(int flyTime, ItemStack stack, Location originLoc, List<Player> playerList) {
        // 1. 初始化点阵数据
        //    点阵、id、uuid 都是本次执行的局部状态：同一实例会被连续调用，
        //    而爆炸任务延迟执行，这些数据不能挂在实例字段上。
        String[] bitmapLines = font.fromString(text, gap).chars();

        // 2. 计算需要生成的实体总数 (烟花总像素点)
        int totalFireworks = calculateTotalPoints(bitmapLines);

        // 3. 生成所有烟花实体的 ID 和 UUID (所有玩家看到的是同一组实体 ID)
        int[] entityIds = new int[totalFireworks];
        UUID[] entityUuids = new UUID[totalFireworks];
        FireworkSender.calculateEntityIds(entityIds, entityUuids);

        // 4. 每个火箭独立掷一次发射声，没掷中的整支静音
        boolean[] silent = new boolean[totalFireworks];
        ThreadLocalRandom rd = ThreadLocalRandom.current();
        for (int i = 0; i < totalFireworks; i++) {
            silent[i] = rd.nextDouble() >= launchChance;
        }

        // 5. 发送生成包 (根据是否固定朝向分流处理)
        if (face != null) {
            handleFixedDirection(bitmapLines, entityIds, entityUuids, silent, stack, originLoc, playerList);
        } else {
            handleDynamicDirection(bitmapLines, entityIds, entityUuids, silent, stack, originLoc, playerList);
        }

        // 6. 创建爆炸任务 (延迟引爆)
        //    按文字最高的一行（每行比基准点高 size）裁剪飞行时间，
        //    保证整块字都在客户端 64 格的渲染范围内引爆。
        Location topMost = originLoc.clone().add(0, bitmapLines.length * size, 0);
        int safeFlyTime = FlightUtil.clampFlyTime(flyTime, topMost, playerList);

        new BukkitRunnable() {
            @Override
            public void run() {
                FireworkUtil.broadcastFireworkExplosion(playerList, explosionChance, entityIds);
            }
        }.runTaskLater(PowerfulFireworks.getInstance(), safeFlyTime);
    }

    /**
     * 处理固定朝向的烟花
     */
    private void handleFixedDirection(String[] bitmapLines, int[] entityIds, UUID[] entityUuids, boolean[] silent,
                                      ItemStack stack, Location originLoc, List<Player> recipients) {
        int length = bitmapLines[0].length();
        List<Location> currentLocs = new ArrayList<>();

        int xMod = face.getModX();
        int zMod = face.getModZ();

        // 计算起始 X 和 Z（水平居中）
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
                    sendPacketToAll(recipients, entityIds[currentIdIndex], entityUuids[currentIdIndex], loc, stack, silent[currentIdIndex]);
                    currentIdIndex++;
                }
            }
        }
    }

    /**
     * 处理动态朝向的烟花 (修改为从下往上生成)
     */
    private void handleDynamicDirection(String[] bitmapLines, int[] entityIds, UUID[] entityUuids, boolean[] silent,
                                        ItemStack stack, Location originLoc, List<Player> recipients) {
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
                        sendSinglePacket(p, entityIds[currentIdIndex], entityUuids[currentIdIndex], loc, stack, silent[currentIdIndex]);
                        currentIdIndex++;
                    }
                }
            }
        }
    }

    // 发送给单个玩家
    private void sendSinglePacket(Player p, int id, UUID uuid, Location loc, ItemStack stack, boolean silent) {
        NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id, uuid, loc);
        NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id, stack, silent);
        nms.sendAddEntity(p, add, data);
    }

    // 广播给玩家
    private void sendPacketToAll(List<Player> players, int id, UUID uuid, Location loc, ItemStack stack, boolean silent) {
        NMSAddEntityPacket add = nms.createAddFireworkEntityPacket(id, uuid, loc);
        NMSEntityDataPacket data = nms.createFireworkEntityDataPacket(id, stack, silent);
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
