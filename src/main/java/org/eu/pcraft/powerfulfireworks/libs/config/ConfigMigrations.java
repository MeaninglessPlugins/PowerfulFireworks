package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * {@link ConfigMigration} 的默认实现工厂（对齐 ConfigMe {@code PlainMigrationService} 的
 * 现成迁移策略）。
 *
 * <p>保守纪律：返回 true 只标记"需要迁移"，是否落盘由调用方显式裁决——绝不自动写盘。</p>
 */
public final class ConfigMigrations {

    private ConfigMigrations() {}

    /** 无操作迁移（从不触发）。 */
    public static ConfigMigration noop() {
        return (root, values) -> false;
    }

    /**
     * 版本链式迁移：包装 {@link ConfigVersions#migrate}（步骤按 from 版本注册，就地改 root）。
     * 仅当至少执行了一步版本迁移时返回 true。
     */
    public static ConfigMigration versioned(
            int currentVersion, Map<Integer, UnaryOperator<Map<String, Object>>> stepByFrom) {
        return (root, values) -> ConfigVersions.migrate(root, currentVersion, stepByFrom);
    }

    /**
     * 版本链 + 值合法性触发（对齐 ConfigMe {@code PlainMigrationService}）：版本步骤迁移过，
     * 或存在任一不在资源中完全有效的条目（缺失/非法，见 {@link ConfigValues#allValidInResource()}）
     * 即返回 true——"模型与磁盘分叉，调用方应考虑补键/重写后显式落盘"。
     *
     * <p>注意：与 ConfigMe 的"返回 true 即自动重建保存"不同，本库返回 true 后仍由调用方显式
     * 走 {@link ConfigFileStore#upgrade(int)}（putIfAbsent 补键）或 {@code set/save}，绝不自动
     * 覆盖管理员值。</p>
     */
    public static ConfigMigration versionedWithValidity(
            int currentVersion, Map<Integer, UnaryOperator<Map<String, Object>>> stepByFrom) {
        return (root, values) -> {
            boolean migrated = ConfigVersions.migrate(root, currentVersion, stepByFrom);
            return migrated || !values.allValidInResource();
        };
    }
}
