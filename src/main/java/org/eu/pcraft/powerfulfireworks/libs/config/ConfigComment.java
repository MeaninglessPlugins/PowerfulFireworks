package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 条目注释（可重复声明多行）：写入默认配置文件时渲染为该条目上方的 {@code # } 注释行；也供
 * 运行时注释查询的默认文案。可标注于 POJO 字段或 record 组件。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigComment {
    /** 每行一条注释文本（自动前缀 "# "；空串表示空注释行）。 */
    String[] value();
}
