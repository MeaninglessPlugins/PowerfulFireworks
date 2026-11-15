package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式配置路径覆写（点号路径，如 {@code "max-distance"}、{@code "stats.cooldown-ms"}）；
 * 缺省 = 字段名 kebab-case（{@code autoUse} → {@code auto-use}）。可标注于 POJO 字段或
 * record 组件。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigPath {
    String value();
}
