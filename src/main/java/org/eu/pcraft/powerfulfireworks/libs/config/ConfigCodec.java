package org.eu.pcraft.powerfulfireworks.libs.config;

/**
 * 自定义类型序列化注册点：把非内建字段类型编码为 YAML 可序列化值（标量/流转义字符串或流
 * List）并在装载时还原。经 {@link ConfigCodec @ConfigCodec} 标注在字段上（不设全局注册表）。
 *
 * <p>语义：{@code fromConfig} 对非法输入抛 {@link RuntimeException} 时，装载按字段默认值
 * 静默回落（与家族 Values 语义一致）。{@code toConfig} 目前支持标量/流 List；返回
 * {@code Map} 尚未支持（默认发射会以 IllegalArgumentException 明确拒绝）。</p>
 */
public interface ConfigCodec<T> {

    /** 编码为 YAML 可序列化值（String/Number/Boolean/枚举/List；暂不支持 Map）。 */
    Object toConfig(T value);

    /** 从解析值还原（非法输入可抛 {@link RuntimeException}，装载方回落默认）。 */
    T fromConfig(Object raw);
}
