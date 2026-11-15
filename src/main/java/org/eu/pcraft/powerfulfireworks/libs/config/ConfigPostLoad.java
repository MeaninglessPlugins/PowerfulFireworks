package org.eu.pcraft.powerfulfireworks.libs.config;

/**
 * 装载后处理钩子：注解绑定完成后、返回调用方前执行一次，用于把原始绑定字段（如
 * {@code List<String>} 材质 id）解析为派生字段（如基于外部注册表的 {@code Set<Material>}），
 * 并可追加 {@link ConfigIssue}。经 {@link Bindings#load} 重载与 {@link ConfigFileStore#load}
 * 重载传入；store 的 reload / set 重绑定同样触发（post-load 保持随每次快照刷新）。
 *
 * <p>语义：hook 内对类型零值/缺失回落后的字段做领域解析；抛 {@link RuntimeException} 由
 * 调用方决定（lib 不在 hook 内兜底，避免掩盖领域逻辑错误）。</p>
 */
@FunctionalInterface
public interface ConfigPostLoad<T> {

    /** 在绑定模型上执行后处理；可在模型上写派生字段，也可向 {@code issues} 追加诊断。 */
    void apply(T model, IssueCollector issues);
}
