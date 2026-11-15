package org.eu.pcraft.powerfulfireworks.libs.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 配置文件生命周期（纯 JDK、零 Bukkit 依赖）。
 *
 * <p>家族默认写策略 = 运行期永不自动写回；物理写盘只经 {@link #writeAtomic} 且仅由显式
 * 场景（升级补键/管理命令）触发。首跑落盘为**原样复制**（注释逐字保留），不依赖
 * Bukkit {@code JavaPlugin}（消费方传 classloader）。</p>
 */
public final class ConfigFile {

    private ConfigFile() {}

    /**
     * 首跑落盘：资源不存在时才复制到 {@code dataFolder.resourcePath}（目录自动创建）。
     * 目标已存在一律不动（不覆盖管理员修改）。资源缺失抛 {@link IOException}。
     *
     * @return 是否新建了文件
     */
    public static boolean copyDefaultIfMissing(Path dataFolder, String resourcePath, ClassLoader loader)
            throws IOException {
        return copyDefaultIfMissing(dataFolder, resourcePath, loader, resourcePath);
    }

    /**
     * 首跑落盘（双路径版）：classpath 默认资源可在子目录（如 {@code defaults/config.yml}），
     * 而数据目录里的文件名保持运维可见的相对路径（如 {@code config.yml}）。
     *
     * @param targetPath 数据目录下的相对目标路径
     * @param resourcePath classpath 资源路径
     * @return 是否新建了文件
     */
    public static boolean copyDefaultIfMissing(
            Path dataFolder, String targetPath, ClassLoader loader, String resourcePath) throws IOException {
        Path target = dataFolder.resolve(targetPath);
        if (Files.exists(target)) {
            return false;
        }
        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("missing bundled resource: " + resourcePath);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    /** 读文件为 UTF-8 字符串并剥离 BOM（缺失/IO 错抛 {@link IOException}）。 */
    public static String readUtf8(Path file) throws IOException {
        String s = Files.readString(file, StandardCharsets.UTF_8);
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    /**
     * 原子写：同目录临时文件 + rename（优先 ATOMIC_MOVE，不支持则回退普通替换），
     * 崩溃一致（对齐 persist 先例）。目录自动创建。
     */
    public static void writeAtomic(Path file, String content) throws IOException {
        Path dir = file.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
