package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;
import org.yaml.snakeyaml.comments.CommentLine;

/**
 * 注释归属解析：YAML 文本 → 各条目（点号路径）关联的块注释（条目上方 {@code #} 行）与
 * 行内注释（条目值同一行的尾部 {@code #} 注释）。
 *
 * <p>注释文本归一化后返回：去掉前导空白与 {@code #}，再去掉一个前导空格
 * （{@code "# 最大距离"} → {@code "最大距离"}）。读取不改动原文本；修改走
 * {@link ConfigDoc}。</p>
 */
public final class YamlComments {

    /** 单条目的注释归属。块注释空列表 / 行内注释 null 表示没有。 */
    public record Entry(String path, List<String> blockComments, String inlineComment) {

        public Entry {
            blockComments = List.copyOf(blockComments);
        }
    }

    private YamlComments() {}

    /** 指定条目的注释归属；路径不存在返回 {@code null}。 */
    public static Entry commentsAt(String text, String path) {
        DocAst ast = DocAst.parse(text);
        org.yaml.snakeyaml.nodes.NodeTuple tuple = ast.findTuple(ConfigPaths.split(path));
        if (tuple == null) {
            return null;
        }
        return ast.entryOf(tuple, path);
    }

    /** 全部带注释的叶子条目（文档序；嵌套以点号路径展开）。 */
    public static List<Entry> entries(String text) {
        DocAst ast = DocAst.parse(text);
        List<Entry> out = new ArrayList<>();
        ast.collectCommented(ast.root(), "", out);
        return List.copyOf(out);
    }

    /** 块注释行归一化：去前导空白与 # 及一个前导空格。 */
    static String normalizeCommentLine(CommentLine line) {
        String v = line.getValue();
        if (v == null) {
            return "";
        }
        int i = 0;
        while (i < v.length() && (v.charAt(i) == ' ' || v.charAt(i) == '\t')) {
            i++;
        }
        if (i < v.length() && v.charAt(i) == '#') {
            i++;
        }
        if (i < v.length() && v.charAt(i) == ' ') {
            i++;
        }
        return v.substring(i);
    }

    /** 行内注释提取：{@code from} 列之后首个 {@code #} 到行尾（去 "# " 前缀）；无则 null。 */
    static String inlineAt(String line, int from) {
        int i = line.indexOf('#', from);
        if (i < 0) {
            return null;
        }
        String rest = line.substring(i + 1);
        if (rest.startsWith(" ")) {
            rest = rest.substring(1);
        }
        return rest.isEmpty() ? "" : rest;
    }
}
