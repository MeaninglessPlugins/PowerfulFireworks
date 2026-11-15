package org.eu.pcraft.powerfulfireworks.libs.config;

import java.io.IOException;
import java.util.List;

/**
 * 多文件配置分组（公共 API）：把若干 {@link ConfigFileStore} 聚合为一次
 * {@link #saveAll()}/{@link #reloadAll()}（PepperClaim 多文件、PepperTreeCut
 * config+profiles 场景的薄装载层）。
 */
public final class ConfigGroup {

    private final List<ConfigFileStore<?>> stores;

    public static ConfigGroup of(ConfigFileStore<?>... stores) {
        return new ConfigGroup(List.of(stores));
    }

    private ConfigGroup(List<ConfigFileStore<?>> stores) {
        this.stores = stores;
    }

    /** 全部原子落盘。 */
    public void saveAll() throws IOException {
        for (ConfigFileStore<?> s : this.stores) {
            s.save();
        }
    }

    /** 全部重载（各 store 独立保留旧快照语义）。 */
    public void reloadAll() {
        for (ConfigFileStore<?> s : this.stores) {
            s.reload();
        }
    }

    public int size() {
        return this.stores.size();
    }
}
