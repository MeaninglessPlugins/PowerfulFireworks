package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMap;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

/**
 * 注释感知的配置文档（公共 API）：可查询条目值/块注释/行内注释，并对条目执行**字节保真**
 * 的值与注释修改——每次 <code>with*</code> 返回新文档，磁盘其它字节逐字不变（家族写回路线，
 * 与 <code>YamlMerge</code> 同一原则）。
 *
 * <p>值修改：只重建目标键所在行（保留行内注释与其它行）；块注释修改：只替换该条目标的正上
 * 方注释区。路径不存在抛 {@link IllegalArgumentException}。</p>
 */
public final class ConfigDoc {

    private final DocAst ast;

    private ConfigDoc(DocAst ast) {
        this.ast = ast;
    }

    /** 解析文本为文档（重复键/多文档/顶层非映射抛 {@code YamlParseException}）。 */
    public static ConfigDoc parse(String text) {
        return new ConfigDoc(DocAst.parse(text));
    }

    /** 当前全文。 */
    public String text() {
        return this.ast.text();
    }

    /** 条目是否存在（沿路径导航）。 */
    public boolean contains(String path) {
        return this.ast.findTuple(ConfigPaths.split(path)) != null;
    }

    /** 条目解析出的值（YamlMap 语义；缺失返回 null）。 */
    public Object value(String path) {
        return ConfigSchema.rawAt(YamlMap.parse(this.ast.text()), path);
    }

    /** 条目标的正上方块注释（归一化文本；无则空列表）。 */
    public List<String> blockComments(String path) {
        NodeTuple tuple = this.ast.findTuple(ConfigPaths.split(path));
        if (tuple == null) {
            return List.of();
        }
        return DocAst.blockCommentsOf(tuple.getKeyNode());
    }

    /** 条目标的行内注释（归一化；无则 null）。 */
    public String inlineComment(String path) {
        NodeTuple tuple = this.ast.findTuple(ConfigPaths.split(path));
        if (tuple == null) {
            return null;
        }
        return this.ast.entryOf(tuple, path).inlineComment();
    }

    /**
     * 文档头部注释：首个顶层键上方的连续块注释（归一化；无则空列表）。与首键自身的
     * {@link #blockComments(String)} 指向同一区域——头部注释即文件顶部注释，语义一致。
     */
    public List<String> header() {
        NodeTuple first = firstTopTuple();
        if (first == null) {
            return List.of();
        }
        return DocAst.blockCommentsOf(first.getKeyNode());
    }

    /**
     * 设置/清空文档头部注释（只动头区，首键行内注释与其它字节不动）：{@code lines} 为注释行
     * （无 "#" 前缀），空列表删除头部。
     *
     * @throws IllegalArgumentException 文档无顶层条目（无法锚定头部）
     */
    public ConfigDoc withHeader(List<String> lines) {
        List<String> safe = lines == null ? List.of() : lines;
        DocAst old = this.ast;
        NodeTuple first = firstTopTuple();
        if (first == null) {
            if (safe.isEmpty()) {
                return this;
            }
            throw new IllegalArgumentException("文档无顶层条目，无法锚定头部注释");
        }
        ScalarNode key = (ScalarNode) first.getKeyNode();
        String[] ls = old.lines();
        int keyLine = key.getStartMark().getLine();
        int keyIndent = key.getStartMark().getColumn();

        List<CommentLine> existing = first.getKeyNode().getBlockComments();
        int blockStart = keyLine;
        if (existing != null && !existing.isEmpty()) {
            blockStart = existing.get(0).getStartMark().getLine();
        }
        int blockRemove = keyLine - blockStart;

        List<String> blockLines = new ArrayList<>();
        if (!safe.isEmpty()) {
            if (blockRemove == 0 && blockStart > 0 && !ls[blockStart - 1].isBlank()) {
                blockLines.add("");
            }
            for (String c : safe) {
                blockLines.add(indentOf(keyIndent) + (c.isEmpty() ? "#" : "# " + c));
            }
        }
        return applyEdit(old, blockStart, blockRemove, blockLines);
    }

    private NodeTuple firstTopTuple() {
        List<NodeTuple> tuples = this.ast.root().getValue();
        return tuples.isEmpty() ? null : tuples.get(0);
    }

    /** 直接子条目：名称 + 解析值（节 = Map）+ 是否节。 */
    public record Entry(String name, Object value, boolean section) {}

    /**
     * 某节（或根，路径空串）的直接子条目，按文档序。
     *
     * @throws IllegalArgumentException 路径不是节
     */
    public List<Entry> children(String path) {
        Map<String, Object> rootMap = YamlMap.parse(this.ast.text());
        Map<String, Object> sectionMap;
        if (path.isEmpty()) {
            sectionMap = rootMap;
        } else {
            Object node = ConfigSchema.rawAt(rootMap, path);
            if (!(node instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("非配置节: " + path);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            sectionMap = typed;
        }
        List<Entry> out = new ArrayList<>(sectionMap.size());
        for (Map.Entry<String, Object> e : sectionMap.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue(), e.getValue() instanceof Map<?, ?>));
        }
        return List.copyOf(out);
    }

    /**
     * 在指定节（或根，路径空串）内 putIfAbsent 合并默认值：仅插入缺失键（嵌套节整块渲染），
     * 已有键与其它字节逐字不动（字节保真）。
     *
     * @throws IllegalArgumentException 路径不存在或不是节
     */
    public ConfigDoc mergeDefaults(String path, Map<String, Object> defaults) {
        DocAst old = this.ast;
        MappingNode section;
        if (path.isEmpty()) {
            section = old.root();
        } else {
            NodeTuple tuple = requireTuple(old, path);
            if (!(tuple.getValueNode() instanceof MappingNode mn)) {
                throw new IllegalArgumentException("非配置节: " + path);
            }
            section = mn;
        }

        Set<String> existing = new HashSet<>();
        for (NodeTuple t : section.getValue()) {
            if (t.getKeyNode() instanceof ScalarNode kn) {
                existing.add(kn.getValue());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : defaults.keySet()) {
            if (!existing.contains(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            return this;
        }

        int childIndent;
        int anchor;
        if (section.getValue().isEmpty()) {
            anchor = section.getStartMark().getLine() + 1;
            childIndent = section.getStartMark().getColumn() + 2;
        } else {
            ScalarNode firstKey = (ScalarNode) section.getValue().get(0).getKeyNode();
            childIndent = firstKey.getStartMark().getColumn();
            org.yaml.snakeyaml.nodes.Node lastValue =
                    section.getValue().get(section.getValue().size() - 1).getValueNode();
            anchor = DocAst.contentEndLine(lastValue) + 1;
        }

        List<String> block = new ArrayList<>();
        for (String key : missing) {
            renderEntry(block, childIndent, key, defaults.get(key));
        }
        return applyEdit(old, anchor, 0, block);
    }

    private static void renderEntry(List<String> out, int indent, String key, Object value) {
        if (value instanceof Map<?, ?> m) {
            out.add(indentOf(indent) + key + ":");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                renderEntry(out, indent + 2, String.valueOf(e.getKey()), e.getValue());
            }
        } else {
            out.add(indentOf(indent) + key + ": " + YamlScalar.encode(value));
        }
    }

    /** 把条目值替换为 {@code value}（经标量发射器编码；List → flow list）。 */
    public ConfigDoc withValue(String path, Object value) {
        return withValueText(path, YamlScalar.encode(value));
    }

    /**
     * 把条目值替换为给定的 YAML 标量文本（调用方负责正确转义）。保留行内注释，删除/覆盖
     * 旧值占用的全部行。
     */
    public ConfigDoc withValueText(String path, String yamlScalarText) {
        DocAst old = this.ast;
        NodeTuple tuple = requireTuple(old, path);
        ScalarNode key = (ScalarNode) tuple.getKeyNode();
        org.yaml.snakeyaml.nodes.Node value = tuple.getValueNode();
        int keyLine = key.getStartMark().getLine();

        String newLine;
        boolean sameLine = value.getStartMark().getLine() == value.getEndMark().getLine();
        if (sameLine) {
            String prefix =
                    old.lines()[keyLine].substring(0, value.getStartMark().getColumn());
            String trail = old.lines()[keyLine].substring(value.getEndMark().getColumn());
            newLine = prefix + yamlScalarText + trail;
            return applyEdit(old, keyLine, 1, List.of(newLine));
        }
        // 多行值（块标量/块序列/嵌套映射）：整段替换为单行 "key: <text>"
        String indent = old.lines()[keyLine].substring(0, key.getStartMark().getColumn());
        String trail = inlineTrail(old.lines()[keyLine], key.getEndMark().getColumn());
        newLine = indent + key.getValue() + ": " + yamlScalarText + trail;
        int endLine = DocAst.contentEndLine(value);
        return applyEdit(old, keyLine, endLine - keyLine + 1, List.of(newLine));
    }

    /** 替换条目注释：{@code block} 为块注释行（无 "#" 前缀，空列表删除）、{@code inline}
     *  为行内注释（null 删除）。 */
    public ConfigDoc withComments(String path, List<String> block, String inline) {
        List<String> blockSafe = block == null ? List.of() : block;
        DocAst old = this.ast;
        NodeTuple tuple = requireTuple(old, path);
        ScalarNode key = (ScalarNode) tuple.getKeyNode();
        String[] lines = old.lines();
        int keyLine = key.getStartMark().getLine();
        int keyIndent = key.getStartMark().getColumn();

        // 块注释区：[首注释行 .. 键行-1]
        List<org.yaml.snakeyaml.comments.CommentLine> existing =
                tuple.getKeyNode().getBlockComments();
        int blockStart = keyLine;
        if (existing != null && !existing.isEmpty()) {
            blockStart = existing.get(0).getStartMark().getLine();
        }
        int blockRemove = keyLine - blockStart;

        List<String> blockLines = new ArrayList<>();
        if (!blockSafe.isEmpty()) {
            if (blockRemove == 0 && blockStart > 0 && !lines[blockStart - 1].isBlank()) {
                blockLines.add("");
            }
            for (String c : blockSafe) {
                blockLines.add(indentOf(keyIndent) + (c.isEmpty() ? "#" : "# " + c));
            }
        }

        // 行内注释
        int inlineFrom = key.getEndMark().getColumn();
        StringBuilder keyLineSb = new StringBuilder(lines[keyLine]);
        int oldInline = keyLineSb.indexOf("#", inlineFrom);
        if (inline == null) {
            if (oldInline >= 0) {
                keyLineSb.setLength(oldInline);
                while (keyLineSb.length() > 0 && keyLineSb.charAt(keyLineSb.length() - 1) == ' ') {
                    keyLineSb.setLength(keyLineSb.length() - 1);
                }
            }
        } else if (oldInline >= 0) {
            keyLineSb.replace(oldInline, keyLineSb.length(), inline.isEmpty() ? "#" : "# " + inline);
        } else {
            keyLineSb.append(inline.isEmpty() ? " #" : " # " + inline);
        }

        // 合并两块编辑：均使用原始行号坐标（applyEdits 内部统一做偏移换算）
        List<Edit> edits = new ArrayList<>();
        if (blockRemove > 0 || !blockLines.isEmpty()) {
            edits.add(new Edit(blockStart, blockRemove, blockLines));
        }
        edits.add(new Edit(keyLine, 1, List.of(keyLineSb.toString())));
        return applyEdits(ast, edits);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static final class Edit {
        final int pos;
        final int remove;
        final List<String> insert;

        Edit(int pos, int remove, List<String> insert) {
            this.pos = pos;
            this.remove = remove;
            this.insert = insert;
        }
    }

    private static ConfigDoc applyEdit(DocAst ast, int pos, int remove, List<String> insert) {
        return applyEdits(ast, List.of(new Edit(pos, remove, insert)));
    }

    private static ConfigDoc applyEdits(DocAst ast, List<Edit> edits) {
        List<Edit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(e -> e.pos));
        List<String> result = new ArrayList<>(java.util.Arrays.asList(ast.lines()));
        int offset = 0;
        for (Edit e : sorted) {
            int p = e.pos + offset;
            List<String> suffix = new ArrayList<>(result.subList(p + e.remove, result.size()));
            result = new ArrayList<>(result.subList(0, p));
            result.addAll(e.insert);
            result.addAll(suffix);
            offset += e.insert.size() - e.remove;
        }
        return new ConfigDoc(DocAst.parse(String.join("\n", result)));
    }

    private static NodeTuple requireTuple(DocAst ast, String path) {
        NodeTuple tuple = ast.findTuple(ConfigPaths.split(path));
        if (tuple == null) {
            throw new IllegalArgumentException("无此配置条目: " + path);
        }
        return tuple;
    }

    private static String indentOf(int columns) {
        return " ".repeat(columns);
    }

    /** 键行行内注释保留片段（含行内注释则原样保留，无则空串）。 */
    private static String inlineTrail(String keyLine, int from) {
        int i = keyLine.indexOf('#', from);
        return i < 0 ? "" : keyLine.substring(i);
    }
}
