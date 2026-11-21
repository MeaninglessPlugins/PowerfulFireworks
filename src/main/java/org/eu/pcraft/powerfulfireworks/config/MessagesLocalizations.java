package org.eu.pcraft.powerfulfireworks.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.afternode.commons.localizations.ILocalizations;
import org.slf4j.Logger;

/**
 * 给本地化查表加一层兜底。
 *
 * <p>底层实现在键不存在时返回字符串 {@code "null"}（它直接对 ConfigurationSection 取值再
 * {@code String.valueOf}）。玩家看到 {@code null} 没有任何线索，也无法区分"配置写错"和"代码写错键"，
 * 所以这里把缺失的键换成键名本身，并只警告一次。
 */
public final class MessagesLocalizations implements ILocalizations {

    private final ILocalizations delegate;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public MessagesLocalizations(ILocalizations delegate, Logger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public String get(String key) {
        return guard(key, delegate.get(key));
    }

    @Override
    public String get(String key, Map<String, Object> placeholders) {
        return guard(key, delegate.get(key, placeholders));
    }

    @Override
    public String get(String key, String... placeholders) {
        return guard(key, delegate.get(key, placeholders));
    }

    @Override
    public Set<String> keys() {
        return delegate.keys();
    }

    private String guard(String key, String value) {
        if (value != null && !"null".equals(value)) {
            return value;
        }
        if (warned.add(key)) {
            logger.warn("messages.yml 里没有键 {}，界面上会显示键名代替文本", key);
        }
        return key;
    }
}
