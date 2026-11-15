package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数值范围校验：装载时越界记 WARN；{@link #clamp()}=false（默认）回落声明默认值，
 * {@link #clamp()}=true 则夹紧到边界并保留该值（Minecart 类"修正为边界"语义）。
 * 缺省端（NaN）不限。可标注于 POJO 字段或 record 组件的数值型条目（int/long/double）。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigRange {
    double min() default Double.NaN;

    double max() default Double.NaN;

    /** true = 越界夹紧到边界（保留数值，WARN）；false = 越界回落默认值（WARN）。 */
    boolean clamp() default false;
}
