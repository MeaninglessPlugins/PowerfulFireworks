package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置文件头部注释（类级）：默认文件发射时渲染为文档顶部的 {@code # } 注释行（对应
 * Configurate 的 header 概念）。运行时读写头部走 {@link ConfigDoc#header()} /
 * {@link ConfigDoc#withHeader(java.util.List)}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigHeader {
    /** 每行一条注释文本（自动前缀 "# "；空串表示空注释行）。 */
    String[] value();
}
