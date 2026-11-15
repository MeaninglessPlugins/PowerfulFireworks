package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.List;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMerge;

/**
 * 升级补键：默认模板 vs 磁盘 → 只补缺失键块（含注释），磁盘其它字节不动。
 *
 * <p>薄层包装 {@link YamlMerge}：{@link #plan} 纯内存算缺失清单（供 diffLog 通告，不碰盘）；
 * {@link #apply} 出合并文本，物理写盘由消费方走 {@link ConfigFile#writeAtomic}（先备份）。</p>
 */
public final class UpgradePatch {

    private UpgradePatch() {}

    /** 补键计划（缺失路径 = 点号文案，仅供通告/日志）。 */
    public record Plan(boolean changed, List<String> missingPaths) {
        public Plan {
            missingPaths = List.copyOf(missingPaths);
        }
    }

    /** 纯内存计算缺失清单，不产出合并文本也不碰盘。 */
    public static Plan plan(String diskText, String templateText) {
        YamlMerge.Result r = YamlMerge.merge(diskText, templateText);
        return new Plan(r.changed(), r.insertedPaths());
    }

    /** 计算合并文本（磁盘字节原样 + 只插缺失键块）；不落盘。 */
    public static YamlMerge.Result apply(String diskText, String templateText) {
        return YamlMerge.merge(diskText, templateText);
    }
}
