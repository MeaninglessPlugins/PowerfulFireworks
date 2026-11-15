package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;

/** 点号路径小工具（package-private，注解绑定层与文档引擎使用）。 */
final class ConfigPaths {

    private ConfigPaths() {}

    /** 校验点号路径合法：非空、每段非空且不含注释符/换行。 */
    static void checkKey(String dotted) {
        if (dotted == null || dotted.isBlank()) {
            throw new IllegalArgumentException("config path must not be blank");
        }
        for (String seg : dotted.split("\\.")) {
            if (seg.isBlank() || seg.indexOf('#') >= 0 || seg.contains("\n")) {
                throw new IllegalArgumentException("invalid config path segment: " + seg);
            }
        }
    }

    /** 拆分为段列表。 */
    static List<String> split(String dotted) {
        List<String> out = new ArrayList<>();
        for (String seg : dotted.split("\\.")) {
            out.add(seg);
        }
        return out;
    }
}
