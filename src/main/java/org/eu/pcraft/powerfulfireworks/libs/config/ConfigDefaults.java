package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认实例来源标记：标注在模型类自己的 {@code static} 无参工厂方法上，绑定层用它取代"类型零值"
 * 作为 schema 默认值。
 *
 * <p>record 模型没有字段初值，缺省时所有键的默认值退化为类型零值（{@code 0}/{@code false}/
 * {@code ""}）。对"缺键即回落代码里的真实默认"的模型（如 PepperClaim 的
 * {@code CoreSettings.defaults()}），这会把缺失的 {@code storage.host} 变成空串。标注本注解后，
 * 绑定层调用该工厂取默认实例，逐组件读取默认值；嵌套 record 节与 {@code List<record>}/
 * {@code Map&lt;String,record&gt;} 元素同样按此解析（元素自身的工厂优先，无工厂则类型零值）。</p>
 *
 * <p>方法必须是所属模型类的 {@code static} 无参方法且返回该模型类型，否则在 schema 构建期抛
 * {@link IllegalArgumentException}。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigDefaults {}
