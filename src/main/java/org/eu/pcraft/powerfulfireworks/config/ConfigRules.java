package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.ConfigValues;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueCollector;

/**
 * 装载完成后的语义校验钩子。
 *
 * <p>约定：这里只看不写。值已经由装载器归一化（类型回落、越界夹紧、codec 解析），校验只负责
 * 根据已知的运行边界补充说明，任何"修正"动作都不应该出现在实现里。</p>
 *
 * @param <T> 配置模型
 */
@FunctionalInterface
public interface ConfigRules<T> {

    void validate(T model, ConfigValues values, IssueCollector issues);
}
