package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义类型序列化注册点（字段/record 组件级）：该字段经指定 {@link ConfigCodec} 与 YAML 值互相
 * 转换，替代内置类型绑定。codec 类须有无参构造。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Codec {
    Class<? extends ConfigCodec<?>> value();
}
