package org.eu.pcraft.powerfulfireworks.libs.config;

/** 配置校验/归一化问题的严重等级。 */
public enum IssueLevel {
    /** 阻断级：配置语义无法成立（如 extends 父不存在、必填缺失）。 */
    ERROR,
    /** 提示级：可回落默认继续（如未知枚举值、未知键、tag 展开为空）。 */
    WARN
}
