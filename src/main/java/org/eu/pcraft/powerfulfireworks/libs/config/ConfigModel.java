package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置模型类标记：嵌套 {@code @ConfigModel} 静态类作为配置节（section），其字段路径以
 * 所属字段为前缀。公共 API 面之一（见 {@link Bindings}）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigModel {}
