package org.eu.pcraft.powerfulfireworks.libs.yaml;

/**
 * YAML 解析失败的结构化异常。
 *
 * <p>{@link #line()} / {@link #column()} 为 1-based；无位置信息时返回 -1。
 * {@link #problem()} 携带 snakeyaml 原问题描述（英文，含键名/上下文）；
 * 中文展示文案由消费方按需本地化组装。</p>
 */
public final class YamlParseException extends RuntimeException {

    private final int line;
    private final int column;
    private final String problem;

    YamlParseException(int line, int column, String problem, Throwable cause) {
        super(buildMessage(line, column, problem), cause);
        this.line = line;
        this.column = column;
        this.problem = problem;
    }

    private static String buildMessage(int line, int column, String problem) {
        if (line < 0) {
            return problem;
        }
        return "第 " + line + " 行, 第 " + column + " 列: " + problem;
    }

    /** 问题所在行号（1-based；无位置信息为 -1）。 */
    public int line() {
        return this.line;
    }

    /** 问题所在列号（1-based；无位置信息为 -1）。 */
    public int column() {
        return this.column;
    }

    /** snakeyaml 原问题描述（英文，含键名/上下文）。 */
    public String problem() {
        return this.problem;
    }
}
