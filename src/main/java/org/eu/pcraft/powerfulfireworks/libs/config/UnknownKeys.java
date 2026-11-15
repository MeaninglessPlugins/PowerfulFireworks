package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单层未知键检测。
 * 递归/任意键段的组织由消费方完成（各插件 profile 结构不同，属领域语义）。
 */
public final class UnknownKeys {

    private UnknownKeys() {}

    /** 返回 {@code map} 中不在 {@code known} 集合里的键（保持 map 迭代顺序）。 */
    public static List<String> unknown(Map<String, Object> map, Set<String> known) {
        List<String> out = new ArrayList<>();
        for (String key : map.keySet()) {
            if (!known.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }
}
