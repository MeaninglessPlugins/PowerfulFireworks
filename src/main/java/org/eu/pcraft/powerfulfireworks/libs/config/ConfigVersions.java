package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * configVersion 链式迁移。
 *
 * <p>在调用方传入的 root 副本上原地执行（物理落盘经写回场景单独裁决）；步骤函数按
 * from 版本注册，每步负责把版本提升到下一步（未提升则视为终点，防死循环）。</p>
 */
public final class ConfigVersions {

    /** 配置根键：整数版本号，缺省 1。 */
    public static final String VERSION_KEY = "configVersion";

    private ConfigVersions() {}

    /** 读取版本：缺失/非数值 → 1。 */
    public static int versionOf(Map<String, Object> root) {
        Object v = root.get(VERSION_KEY);
        return v instanceof Number n ? n.intValue() : 1;
    }

    /**
     * 从当前版本逐级迁移到 {@code currentVersion}（步骤缺省即停在原地）。
     *
     * @param stepByFrom from 版本 → 迁移步骤（就地修改 root，必须自增 {@code configVersion}）
     * @return 是否发生至少一次迁移
     */
    public static boolean migrate(
            Map<String, Object> root, int currentVersion, Map<Integer, UnaryOperator<Map<String, Object>>> stepByFrom) {
        boolean migrated = false;
        int version = versionOf(root);
        while (version < currentVersion) {
            UnaryOperator<Map<String, Object>> step = stepByFrom.get(version);
            if (step == null) {
                break;
            }
            step.apply(root);
            migrated = true;
            int next = versionOf(root);
            if (next <= version) {
                break; // 步骤未提升版本——防死循环
            }
            version = next;
        }
        return migrated;
    }
}
