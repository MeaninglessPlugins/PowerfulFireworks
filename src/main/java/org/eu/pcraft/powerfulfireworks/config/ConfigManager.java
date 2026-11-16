package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.Bindings;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigDoc;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigFile;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigValues;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueCollector;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置文件的装载、归一化与校验。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>装载——文件不存在时写出带注释的默认文件，之后只补新增的键，玩家写过的东西不动；</li>
 *   <li>归一化——类型回落、越界夹紧、codec 解析由框架完成，本类只把"回落了却没说明"的键
 *       补成一条诊断；</li>
 *   <li>校验——可选的 {@link ConfigRules}，只读，不修改任何值。</li>
 * </ul>
 *
 * @param <T> 配置模型，须标注 {@code @ConfigModel}
 */
public class ConfigManager<T> {

    private final Path file;
    private final Class<T> type;
    private final Logger logger;
    private final DiagnosticReporter reporter;
    private final ConfigRules<T> rules;
    private final List<ConfigIssue> issues = new ArrayList<>();

    private T model;
    private ConfigValues values;

    public ConfigManager(Path file, Class<T> type, Logger logger, DiagnosticReporter reporter) {
        this(file, type, logger, reporter, null);
    }

    public ConfigManager(Path file, Class<T> type, Logger logger, DiagnosticReporter reporter, ConfigRules<T> rules) {
        this.file = file;
        this.type = type;
        this.logger = logger;
        this.reporter = reporter;
        this.rules = rules;
    }

    /** 正常装载：会补齐并写回缺失的键。 */
    public void loadConfig() {
        loadConfig(true);
    }

    /**
     * @param writeBack 是否落盘（生成默认文件、补齐缺失键）；只读校验传 {@code false}
     */
    public void loadConfig(boolean writeBack) {
        this.issues.clear();
        try {
            String text;
            if (Files.notExists(this.file)) {
                text = Bindings.defaultsText(this.type);
                if (!writeBack) {
                    this.issues.add(new ConfigIssue(IssueLevel.WARN, "",
                            "配置文件不存在，本次校验全部按默认值进行", "启动或 reload 时会生成"));
                }
                if (writeBack) {
                    if (this.file.getParent() != null) {
                        Files.createDirectories(this.file.getParent());
                    }
                    ConfigFile.writeAtomic(this.file, text);
                }
            } else {
                text = ConfigFile.readUtf8(this.file);
                if (writeBack) {
                    String merged = mergeDefaults(text);
                    if (!merged.equals(text)) {
                        ConfigFile.writeAtomic(this.file, merged);
                        text = merged;
                    }
                }
            }
            bind(text);
        } catch (IOException e) {
            throw new RuntimeException("读取配置 " + this.file + " 失败", e);
        }
        this.reporter.add(this.file.getFileName().toString(), this.issues);
    }

    public T getConfigModule() {
        return this.model;
    }

    /** 最近一次装载的值级状态，供需要区分"写了没有"的规则使用。 */
    public ConfigValues getValues() {
        return this.values;
    }

    /** 最近一次装载的诊断（已同步给 reporter，保留一份便于命令读取计数）。 */
    public List<ConfigIssue> getIssues() {
        return List.copyOf(this.issues);
    }

    private void bind(String text) {
        IssueCollector collected = new IssueCollector();
        Bindings.LoadResult<T> result;
        try {
            result = Bindings.loadWithValues(this.type, YamlMap.parse(text), collected);
        } catch (RuntimeException e) {
            this.issues.add(new ConfigIssue(IssueLevel.ERROR, "",
                    "解析失败，本次使用默认值：" + e.getMessage(), "检查缩进与类型写法"));
            result = Bindings.loadWithValues(this.type, Map.of(), new IssueCollector());
        }

        this.model = result.model();
        this.values = result.values();

        // 类型不符/无法解析的键在框架里是静默回落的：状态记成 INVALID 但没有诊断，这里补上
        reportInvalidValues(collected);

        if (this.rules != null) {
            try {
                this.rules.validate(this.model, this.values, collected);
            } catch (RuntimeException e) {
                this.logger.error("{} 的语义校验失败", this.file.getFileName(), e);
            }
        }

        this.issues.addAll(collected.issues());
    }

    private void reportInvalidValues(IssueCollector collected) {
        if (this.values == null) {
            return;
        }
        Set<String> reported = new HashSet<>();
        for (ConfigIssue issue : collected.issues()) {
            reported.add(issue.path());
        }
        for (Map.Entry<String, ConfigValues.Entry> entry : this.values.entries().entrySet()) {
            if (entry.getValue().status() != ConfigValues.Status.INVALID || reported.contains(entry.getKey())) {
                continue;
            }
            collected.add(new ConfigIssue(IssueLevel.ERROR, entry.getKey(),
                    "值无效（类型不符或无法解析），已回落默认值", "检查该键的类型与写法"));
        }
    }

    private String mergeDefaults(String text) {
        Map<String, Object> defaults;
        try {
            defaults = YamlMap.parse(Bindings.defaultsText(this.type));
        } catch (RuntimeException e) {
            this.logger.error("生成 {} 的默认配置失败", this.file.getFileName(), e);
            return text;
        }
        if (defaults.isEmpty()) {
            return text;
        }
        try {
            ConfigDoc doc = ConfigDoc.parse(text);
            return mergeSection(doc, "", defaults).text();
        } catch (RuntimeException e) {
            return text;
        }
    }

    /**
     * 递归把默认值里缺失的键补进文档：已存在的节先递归下去补子键，再把整块缺失的节和
     * 标量一次性插到当前节末尾。
     */
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
