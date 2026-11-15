package org.eu.pcraft.powerfulfireworks.libs.yaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 文本模板合并：磁盘 + 默认模板 → 合并文本（只增缺失键，磁盘其它字节原样）。
 *
 * <p>为什么用文本合并而不是 Node 往返：真实语料上 Node 往返会让 60%~75% 的行重排，注释与空行
 * 跟着漂移。语义判断用 {@link YamlMap}（重复键等直接报错），
 * 块来源与插入锚点用 snakeyaml compose 的 AST 行号，磁盘既有字节一律不动。</p>
 */
public final class YamlMerge {

    private YamlMerge() {}

    /** 合并结果。{@code changed=false} 时 {@code merged} 恒等于磁盘原文。 */
    public record Result(String merged, boolean changed, List<String> insertedPaths) {
        public Result {
            insertedPaths = List.copyOf(insertedPaths);
        }
    }

    private record Missing(List<String> path, Node keyNode, Node valueNode) {}

    private record Edit(int pos, List<String> lines) {}

    private static final class ComposeResult {
        final MappingNode root;
        final boolean empty;

        ComposeResult(MappingNode root, boolean empty) {
            this.root = root;
            this.empty = empty;
        }
    }

    /**
     * 磁盘文本与默认模板合并。磁盘缺、模板有的键块（含前置注释，按模板原缩进）插入；
     * 磁盘已存在的路径（无论值类型）一律不动——putIfAbsent 语义。磁盘/模板语法错抛
     * {@link YamlParseException}（同 {@link YamlMap}）。
     */
    public static Result merge(String diskText, String templateText) {
        if (diskText == null || templateText == null) {
            throw new IllegalArgumentException("diskText and templateText must not be null");
        }
        // 语义存在性判断：YamlMap.parse（S8 重复键报错/S12 多文档拒绝/S7 顶层非映射报错）
        Map<String, Object> diskMap = YamlMap.parse(diskText);
        Map<String, Object> tplMap = YamlMap.parse(templateText);

        String[] diskLines = diskText.isBlank() ? new String[0] : diskText.split("\n", -1);
        String[] tplLines = templateText.split("\n", -1);

        ComposeResult diskRoot = compose(diskText);
        ComposeResult tplRoot = compose(templateText);

        List<Missing> missing = new ArrayList<>();
        collectMissing(tplMap, tplRoot.root, diskMap, List.of(), missing);

        if (missing.isEmpty()) {
            return new Result(diskText, false, List.of());
        }

        List<Edit> edits = new ArrayList<>();
        List<String> insertedPaths = new ArrayList<>();
        for (Missing ms : missing) {
            insertedPaths.add(String.join(".", ms.path()));
            List<String> block = blockLines(tplLines, ms.keyNode(), ms.valueNode());
            if (ms.path().size() == 1) {
                // 顶层缺失键 → 磁盘末尾（保留既有末尾换行/空行约定）
                List<String> lines = new ArrayList<>(block);
                if (diskLines.length > 0 && !diskLines[diskLines.length - 1].isEmpty()) {
                    lines.add(0, "");
                }
                if (!lines.get(lines.size() - 1).isEmpty()) {
                    lines.add("");
                }
                edits.add(new Edit(diskLines.length, lines));
            } else {
                // 嵌套缺失键 → 磁盘该 section 最后一个子键之后，模板块重缩进到该层
                List<String> parentSegments = ms.path().subList(0, ms.path().size() - 1);
                Node parentNode = findNode(diskRoot, parentSegments);
                int parentIndent = indentOf(diskLines[parentNode.getStartMark().getLine()]);
                int anchor;
                int targetIndent;
                if (parentNode instanceof MappingNode pn && !pn.getValue().isEmpty()) {
                    targetIndent = indentOf(diskLines[
                            pn.getValue().get(0).getKeyNode().getStartMark().getLine()]);
                    anchor = contentEndLine(
                                    pn.getValue().get(pn.getValue().size() - 1).getValueNode())
                            + 1;
                } else {
                    // 空 section（含 `key:` 空值 = 空标量节点）→ 锚 = 头行后，缩进 = 父缩进 + 2
                    anchor = parentNode.getStartMark().getLine() + 1;
                    targetIndent = parentIndent + 2;
                }
                int keyIndent = indentOf(tplLines[ms.keyNode().getStartMark().getLine()]);
                edits.add(new Edit(anchor, reindent(block, keyIndent, targetIndent)));
            }
        }

        // 升序稳定排序（同锚点保持模板序），插入时用累加偏移对齐原坐标
        edits.sort(Comparator.comparingInt(Edit::pos));
        List<String> result = new ArrayList<>(Arrays.asList(diskLines));
        int offset = 0;
        for (Edit e : edits) {
            result.addAll(e.pos() + offset, e.lines());
            offset += e.lines().size();
        }
        return new Result(String.join("\n", result), true, insertedPaths);
    }

    /** 递归收集缺失路径（最小缺失子树）：父存在才下钻，缺失即整块记入（不覆盖磁盘既有键）。 */
    private static void collectMissing(
            Map<String, Object> tplMap,
            MappingNode tplNode,
            Map<String, Object> diskMap,
            List<String> path,
            List<Missing> out) {
        for (NodeTuple tuple : tplNode.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode kn)) {
                continue;
            }
            String key = kn.getValue();
            List<String> childPath = append(path, key);
            if (!diskMap.containsKey(key)) {
                out.add(new Missing(childPath, tuple.getKeyNode(), tuple.getValueNode()));
                continue;
            }
            Object tv = tplMap.get(key);
            Object dv = diskMap.get(key);
            if (tuple.getValueNode() instanceof MappingNode childTpl && tv instanceof Map<?, ?> tm) {
                if (dv instanceof Map<?, ?> dm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dmc = (Map<String, Object>) dm;
                    collectMissing((Map<String, Object>) tm, childTpl, dmc, childPath, out);
                } else if (dv == null) {
                    // S9：`key:` 空值 → null；视作空 section，子键按缺失插入
                    collectMissing((Map<String, Object>) tm, childTpl, new LinkedHashMap<>(), childPath, out);
                }
                // dv 非 null 非 Map（类型不符）→ 已有键不动
            }
        }
    }

    /** 模板块原文行切片：[前置注释首行 .. 最后一个子元素真实末行]。 */
    private static List<String> blockLines(String[] lines, Node keyNode, Node valueNode) {
        int start = keyNode.getStartMark().getLine();
        List<CommentLine> comments = keyNode.getBlockComments();
        if (comments != null && !comments.isEmpty()) {
            start = comments.get(0).getStartMark().getLine();
        }
        int end = contentEndLine(valueNode);
        return new ArrayList<>(Arrays.asList(lines).subList(start, end + 1));
    }

    /**
     * 节点内容的真实末行。容器节点的 endMark 指向"下一个键"的开头行（block 语义），
     * 必须递归到最后一个子元素的末行；标量即自身末行。
     */
    private static int contentEndLine(Node n) {
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

    /** 嵌套插入重缩进：相对模板基准缩进平移（家族模板同层插入 delta=0，此处为兼容保障）。 */
    private static List<String> reindent(List<String> block, int from, int to) {
        int delta = to - from;
        if (delta == 0) {
            return block;
        }
        List<String> out = new ArrayList<>(block.size());
        for (String line : block) {
            if (line.isBlank()) {
                out.add(line);
                continue;
            }
            if (delta > 0) {
                out.add(" ".repeat(delta) + line);
            } else {
                out.add(line.substring(Math.min(-delta, indentOf(line))));
            }
        }
        return out;
    }

    private static List<String> append(List<String> path, String key) {
        List<String> out = new ArrayList<>(path);
        out.add(key);
        return out;
    }

    /** 按路径段查找节点（返回任意 Node；空值 section 为 ScalarNode，由调用方按空处理）。 */
    private static Node findNode(ComposeResult root, List<String> segments) {
        Node cur = root.root;
        for (String seg : segments) {
            MappingNode m = (MappingNode) cur;
            Node next = null;
            for (NodeTuple t : m.getValue()) {
                if (t.getKeyNode() instanceof ScalarNode kn && kn.getValue().equals(seg)) {
                    next = t.getValueNode();
                    break;
                }
            }
            cur = next;
        }
        return cur;
    }

    private static ComposeResult compose(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setProcessComments(true);
        try {
            Node node = new Yaml(new SafeConstructor(options)).compose(new java.io.StringReader(stripBom(text)));
            if (node == null) {
                return new ComposeResult(
                        new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK), true);
            }
            if (!(node instanceof MappingNode mn)) {
                throw new YamlParseException(-1, -1, "顶层必须是映射（合并器）", null);
            }
            return new ComposeResult(mn, false);
        } catch (YamlParseException e) {
            throw e;
        } catch (YAMLException e) {
            throw new YamlParseException(-1, -1, String.valueOf(e.getMessage()), e);
        }
    }

    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }
}
