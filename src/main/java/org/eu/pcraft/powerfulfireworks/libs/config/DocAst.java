package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * 注释感知的文档 AST（package-private）：YAML 文本 → 行数组 + snakeyaml compose 树
 * （processComments=true），提供路径定位、注释归属、节点坐标与行切片。被
 * {@link YamlComments}（只读解析）与 {@link ConfigDoc}（字节保真编辑）共享。
 */
final class DocAst {

    private final String text;
    private final String[] lines;
    private final MappingNode root;

    private DocAst(String text, String[] lines, MappingNode root) {
        this.text = text;
        this.lines = lines;
        this.root = root;
    }

    static DocAst parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        // 语义门（S1–S16）：重复键/多文档/顶层非映射先由 YamlMap 严格判定，再取坐标
        YamlMap.parse(text);
        String s = stripBom(text);
        String[] lines = s.split("\n", -1);
        Node node = compose(s);
        MappingNode root;
        if (node == null) {
            root = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
        } else if (!(node instanceof MappingNode mn)) {
            // 语义已由 YamlMap.parse 门控（顶层非映射早已抛错），此分支不可达
            throw new IllegalStateException("unexpected non-mapping root after YamlMap gate");
        } else {
            root = mn;
        }
        return new DocAst(s, lines, root);
    }

    String text() {
        return this.text;
    }

    String[] lines() {
        return this.lines;
    }

    MappingNode root() {
        return this.root;
    }

    /** 按点号路径定位叶子元组；路径缺失/中间非映射返回 null。 */
    NodeTuple findTuple(List<String> segments) {
        MappingNode cur = this.root;
        for (int i = 0; i < segments.size(); i++) {
            NodeTuple found = null;
            for (NodeTuple t : cur.getValue()) {
                if (t.getKeyNode() instanceof ScalarNode kn && kn.getValue().equals(segments.get(i))) {
                    found = t;
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            if (i == segments.size() - 1) {
                return found;
            }
            if (!(found.getValueNode() instanceof MappingNode mn)) {
                return null;
            }
            cur = mn;
        }
        return null;
    }

    /** 条目的注释归属（叶子为值节点；节头也支持行内注释）。 */
    YamlComments.Entry entryOf(NodeTuple tuple, String path) {
        Node key = tuple.getKeyNode();
        Node value = tuple.getValueNode();
        List<String> block = blockCommentsOf(key);
        String inline;
        if (!(value instanceof MappingNode)) {
            int keyLine = key.getStartMark().getLine();
            if (value.getStartMark().getLine() == keyLine) {
                inline = YamlComments.inlineAt(
                        this.lines[keyLine], value.getEndMark().getColumn());
            } else {
                inline = YamlComments.inlineAt(
                        this.lines[keyLine], key.getEndMark().getColumn());
            }
        } else {
            inline = YamlComments.inlineAt(
                    this.lines[key.getStartMark().getLine()], key.getEndMark().getColumn());
        }
        return new YamlComments.Entry(path, block, inline);
    }

    /** 深度优先收集带注释的叶子条目（文档序）。 */
    void collectCommented(MappingNode m, String prefix, List<YamlComments.Entry> out) {
        for (NodeTuple t : m.getValue()) {
            if (!(t.getKeyNode() instanceof ScalarNode kn)) {
                continue;
            }
            String path = prefix.isEmpty() ? kn.getValue() : prefix + "." + kn.getValue();
            Node v = t.getValueNode();
            if (v instanceof MappingNode mn) {
                collectCommented(mn, path, out);
                continue;
            }
            YamlComments.Entry e = entryOf(t, path);
            if (!e.blockComments().isEmpty() || e.inlineComment() != null) {
                out.add(e);
            }
        }
    }

    /** 键节点的块注释（归一化文本；过滤空白分隔行——编辑区仍以 CommentLine 原始行号计算）。 */
    static List<String> blockCommentsOf(Node keyNode) {
        List<CommentLine> comments = keyNode.getBlockComments();
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(comments.size());
        for (CommentLine c : comments) {
            String normalized = YamlComments.normalizeCommentLine(c);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return List.copyOf(out);
    }

    /** 节点内容的真实末行（递归到最后一个子元素；与 YamlMerge 同语义）。 */
    static int contentEndLine(Node n) {
        if (n instanceof MappingNode mn) {
            if (mn.getValue().isEmpty()) {
                return n.getStartMark().getLine();
            }
            return contentEndLine(mn.getValue().get(mn.getValue().size() - 1).getValueNode());
        }
        if (n instanceof SequenceNode sn) {
            if (sn.getValue().isEmpty()) {
                return sn.getStartMark().getLine();
            }
            return contentEndLine(sn.getValue().get(sn.getValue().size() - 1));
        }
        return n.getEndMark().getLine();
    }

    private static Node compose(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setProcessComments(true);
        options.setAllowDuplicateKeys(false);
        try {
            return new Yaml(new SafeConstructor(options)).compose(new java.io.StringReader(text));
        } catch (YAMLException e) {
            // 语义已由 YamlMap.parse 门控，此处只防御性兜底
            throw new IllegalStateException("unexpected YAML error after YamlMap gate: " + e.getMessage(), e);
        }
    }

    private static String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
