package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置问题收集器。{@link #issues()} 返回不可变快照；
 * 线程约束：不保证线程安全，供单线程装载/校验流程使用。
 */
public final class IssueCollector {

    private final List<ConfigIssue> issues = new ArrayList<>();

    public void add(ConfigIssue issue) {
        this.issues.add(issue);
    }

    /** 当前全部问题（不可变快照）。 */
    public List<ConfigIssue> issues() {
        return List.copyOf(this.issues);
    }

    /** 是否存在 ERROR 级问题。 */
    public boolean hasErrors() {
        return this.issues.stream().anyMatch(i -> i.level() == IssueLevel.ERROR);
    }

    /** 指定等级的问题数量。 */
    public int count(IssueLevel level) {
        int n = 0;
        for (ConfigIssue i : this.issues) {
            if (i.level() == level) {
                n++;
            }
        }
        return n;
    }
}
