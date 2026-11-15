package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.Map;

/**
 * 可插拔迁移服务（对齐 ConfigMe {@code MigrationService} 的显式接口形态）：在**内存 root 副本**
 * 上执行迁移（改名/删键/补键/按值裁决），返回是否发生了迁移（供调用方裁决是否落盘）。
 *
 * <p>保守纪律：本接口只改内存、绝不自动写盘。{@link ConfigFileStore}
 * 集成时，迁移结果只塑造类型化模型与 {@link #checkAndMigrate} 的返回值标记（{@code migrated()}）；
 * 物理落盘仍走显式 {@link ConfigFileStore#save()} / {@link ConfigFileStore#upgrade(int)}。
 * 若需"值不合法即重写"，由 {@link ConfigValues} 信号驱动决策（见 {@link ConfigMigrations}）。</p>
 *
 * <p>实现方注意：{@code root} 是 {@code ConfigFileStore} 提供的工作副本（顶层浅拷贝），
 * 原地修改即可；返回 true 表示"模型与磁盘已分叉，调用方应考虑落盘"。</p>
 */
@FunctionalInterface
public interface ConfigMigration {

    /**
     * 对内存 root 副本执行迁移并裁决是否需要落盘。
     *
     * @param root 配置文件 root map（可原地修改；顶层为工作副本）
     * @param values 迁移前的值级合法性信号（PRESENT/MISSING/INVALID，供"缺键补默认/值不合法重写"
     *     决策；对齐 ConfigMe {@code areAllValuesValidInResource} 的迁移触发）
     * @return true = 发生了迁移（模型与磁盘分叉，调用方应考虑显式落盘）
     */
    boolean checkAndMigrate(Map<String, Object> root, ConfigValues values);
}
