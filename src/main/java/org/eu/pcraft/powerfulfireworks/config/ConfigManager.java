package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.Bindings;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigDoc;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigFile;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueCollector;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置文件的装载与生成。
 *
 * <p>首次运行写出带注释的默认文件；之后每次装载只把模型里新增、而文件里还没有的键补进去，
 * 已有的内容（包括玩家自己写的注释和顺序）原样保留。</p>
 *
 * @param <T> 配置模型，须标注 {@code @ConfigModel}
 */
public class ConfigManager<T> {

    private final Path file;
    private final Class<T> type;
    private final Logger logger;
    private T model;

    public ConfigManager(Path file, Class<T> type, Logger logger) {
        this.file = file;
        this.type = type;
        this.logger = logger;
    }

    public void loadConfig() {
        try {
            if (Files.notExists(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                ConfigFile.writeAtomic(file, Bindings.defaultsText(type));
            }

            String text = ConfigFile.readUtf8(file);
            Map<String, Object> defaults;
            try {
                defaults = YamlMap.parse(Bindings.defaultsText(type));
            } catch (RuntimeException e) {
                logger.error("生成 {} 的默认配置失败", file.getFileName(), e);
                defaults = Map.of();
            }

            String merged = mergeDefaults(text, defaults);
            if (!merged.equals(text)) {
                ConfigFile.writeAtomic(file, merged);
                text = merged;
            }

            IssueCollector issues = new IssueCollector();
            try {
                model = Bindings.load(type, YamlMap.parse(text), issues);
            } catch (RuntimeException e) {
                logger.error("解析 {} 失败，本次使用默认值", file.getFileName(), e);
                model = Bindings.load(type, Map.of(), new IssueCollector());
            }
            report(issues);
        } catch (IOException e) {
            throw new RuntimeException("读取配置 " + file + " 失败", e);
        }
    }

    public T getConfigModule() {
        return model;
    }

    private void report(IssueCollector issues) {
        if (issues.issues().isEmpty()) {
            return;
        }
        logger.warn("{} 中有 {} 处配置问题：", file.getFileName(), issues.issues().size());
        for (ConfigIssue issue : issues.issues()) {
            logger.warn("  [{}] {}：{}{}", issue.level(), issue.path(), issue.message(),
                    issue.hint().isEmpty() ? "" : "（" + issue.hint() + "）");
        }
    }

    /**
     * 递归把默认值里缺失的键补进文档：已存在的节先递归下去补子键，再把整块缺失的节和
     * 标量一次性插到当前节末尾。
     */
    private static String mergeDefaults(String text, Map<String, Object> defaults) {
        if (defaults.isEmpty()) {
            return text;
        }
        ConfigDoc doc;
        try {
            doc = ConfigDoc.parse(text);
            return mergeSection(doc, "", defaults).text();
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static ConfigDoc mergeSection(ConfigDoc doc, String path, Map<String, Object> defaults) {
        Map<String, Object> insert = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sections = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map) {
                Map<String, Object> child = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    child.put(String.valueOf(e.getKey()), e.getValue());
                }
                sections.put(entry.getKey(), child);
            } else {
                insert.put(entry.getKey(), entry.getValue());
            }
        }

        ConfigDoc out = doc;
        for (Map.Entry<String, Map<String, Object>> entry : sections.entrySet()) {
            String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            if (!out.contains(childPath)) {
                insert.put(entry.getKey(), entry.getValue());
            } else if (out.value(childPath) instanceof Map<?, ?>) {
                out = mergeSection(out, childPath, entry.getValue());
            }
            // 否则说明这个键在文件里被写成了单值（例如 number: 3），保留玩家的写法不动
        }
        return insert.isEmpty() ? out : out.mergeDefaults(path, insert);
    }
}
