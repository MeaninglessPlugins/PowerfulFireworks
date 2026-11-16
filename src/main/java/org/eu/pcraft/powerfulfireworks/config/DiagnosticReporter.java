package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueCollector;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置诊断的唯一出口：按来源文件分组，ERROR 全量打印，WARN 默认截断。
 *
 * <p>装载器（{@link ConfigManager}）与调度器校验只往里塞问题，不需要关心格式。</p>
 */
public final class DiagnosticReporter {

    /** 每个来源最多打印多少条 WARN。 */
    private static final int WARN_LIMIT = 10;

    private final Logger logger;
    private boolean verbose;
    private final Map<String, List<ConfigIssue>> sources = new LinkedHashMap<>();

    public DiagnosticReporter(Logger logger, boolean verbose) {
        this.logger = logger;
        this.verbose = verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void add(String source, IssueCollector issues) {
        this.add(source, issues.issues());
    }

    /** 同一来源下按 (等级, 路径, 文案) 去重，避免框架与规则重复描述同一处。 */
    public void add(String source, Collection<ConfigIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        List<ConfigIssue> target = this.sources.computeIfAbsent(source, key -> new ArrayList<>());
        for (ConfigIssue issue : issues) {
            boolean duplicated = false;
            for (ConfigIssue existing : target) {
                if (existing.level() == issue.level()
                        && existing.path().equals(issue.path())
                        && existing.message().equals(issue.message())) {
                    duplicated = true;
                    break;
                }
            }
            if (!duplicated) {
                target.add(issue);
            }
        }
    }

    public int errors() {
        return count(IssueLevel.ERROR);
    }

    public int warnings() {
        return count(IssueLevel.WARN);
    }

    public boolean hasErrors() {
        return errors() > 0;
    }

    private int count(IssueLevel level) {
        int n = 0;
        for (List<ConfigIssue> list : this.sources.values()) {
            for (ConfigIssue issue : list) {
                if (issue.level() == level) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 输出全部诊断；没有问题时只在 verbose 下留一行"通过"。 */
    public void report() {
        int errors = errors();
        int warnings = warnings();
        if (errors == 0 && warnings == 0) {
            if (this.verbose) {
                this.logger.info("配置校验通过");
            }
            return;
        }

        List<Map.Entry<String, List<ConfigIssue>>> ordered = new ArrayList<>(this.sources.entrySet());
        ordered.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, List<ConfigIssue>> entry : ordered) {
            List<ConfigIssue> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator
                    .comparingInt((ConfigIssue issue) -> issue.level() == IssueLevel.ERROR ? 0 : 1)
                    .thenComparing(ConfigIssue::path));

            int sourceErrors = 0;
            int sourceWarnings = 0;
            for (ConfigIssue issue : sorted) {
                if (issue.level() == IssueLevel.ERROR) {
                    sourceErrors++;
                } else {
                    sourceWarnings++;
                }
            }
            String header = entry.getKey() + " 校验：" + sourceErrors + " 个错误，" + sourceWarnings + " 个警告";
            if (sourceErrors > 0) {
                this.logger.error(header);
            } else {
                this.logger.warn(header);
            }

            int shown = 0;
            int hidden = 0;
            for (ConfigIssue issue : sorted) {
                if (issue.level() == IssueLevel.WARN && !this.verbose) {
                    if (shown >= WARN_LIMIT) {
                        hidden++;
                        continue;
                    }
                    shown++;
                }
                String line = "  [" + issue.level() + "] " + issue.path() + "  " + issue.message()
                        + (issue.hint().isEmpty() ? "" : "（" + issue.hint() + "）");
                if (issue.level() == IssueLevel.ERROR) {
                    this.logger.error(line);
                } else {
                    this.logger.warn(line);
                }
            }
            if (hidden > 0) {
                this.logger.warn("  ……还有 {} 条警告，开启 debug 查看全部", hidden);
            }
        }
    }
}
