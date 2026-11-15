package org.eu.pcraft.powerfulfireworks.libs.config;

/**
 * 单条配置问题。
 *
 * @param level 严重等级
 * @param path  点号文案路径（如 {@code "profiles.10-vanilla.wood"}）；仅用于诊断定位，不参与取值
 * @param message 问题描述（文案由消费方本地化）
 * @param hint 修正建议（可选，可为空串）
 */
public record ConfigIssue(IssueLevel level, String path, String message, String hint) {}
