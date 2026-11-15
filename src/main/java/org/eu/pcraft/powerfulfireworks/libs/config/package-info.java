/**
 * 配置文件工具链（源码取自 PepperLib，MIT License，Copyright (c) 2026 Pepper；
 * 许可全文见仓库根目录的 LICENSE-pepperlib.txt）。
 *
 * <p>包内构成：</p>
 *
 * <ul>
 *   <li>生命周期：{@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigFile}（copy-once / readUtf8 BOM / writeAtomic）；
 *   <li>问题收集：{@link org.eu.pcraft.powerfulfireworks.libs.config.IssueCollector}/{@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue}；
 *   <li>类型化读取：{@link org.eu.pcraft.powerfulfireworks.libs.config.Values}/{@link org.eu.pcraft.powerfulfireworks.libs.config.UnknownKeys}；
 *   <li>版本迁移与升级补键：{@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigVersions}/{@link org.eu.pcraft.powerfulfireworks.libs.config.UpgradePatch}；
 *   <li>自研绑定/注释/运行时存储（0.12.0，替代 ConfigLib 绑定层）：
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.Bindings} 注解驱动绑定（{@code @ConfigModel}/
 *       {@code @ConfigPath}/{@code @ConfigComment}/{@code @ConfigRange}/{@code @Codec}）、
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.YamlComments} 注释归属解析、
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigDoc} 字节保真编辑（含子节点遍历
 *       {@code children}、节内默认合并 {@code mergeDefaults}、文档头注释
 *       {@code header}/{@code withHeader}）、
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.YamlScalar} 标量/flow list/flow map 发射、
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigCodec} 自定义类型序列化注册点、
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigFileStore}/{@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigGroup}
 *       运行时存储与多文件分组。
 *   <li>绑定能力增量（0.13.0，P3 首批三家 TreeCut→Minecart→Union 迁移支撑）：
 *       Map 一等绑定（数值/字符串键映射）、装载后处理钩子
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigPostLoad}、{@code @ConfigRange(clamp=true)}
 *       夹紧模式、NaN/Infinity 拦截、枚举大小写不敏感、emit 兄弟节修复。
 *   <li>对照 ConfigMe 吸收的类型系统扩展（0.14.0）：
 *       类型系统扩展（{@code Optional}/{@code Set}/{@code T[]}/
 *       {@code LocalDate}/{@code LocalTime}/{@code LocalDateTime} 一等绑定）、
 *       值级合法性信号 {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigValues}
 *       （PRESENT/MISSING/INVALID，对齐 {@code PropertyValue.isValidInResource}）、
 *       可插拔迁移服务 {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigMigration}/
 *       {@link org.eu.pcraft.powerfulfireworks.libs.config.ConfigMigrations}
 *       （对齐 {@code MigrationService}/{@code PlainMigrationService}，
 *       保守：只改内存不自动落盘）。
 * </ul>
 *
 * 纯 JDK、零 Bukkit 依赖。
 */
package org.eu.pcraft.powerfulfireworks.libs.config;
