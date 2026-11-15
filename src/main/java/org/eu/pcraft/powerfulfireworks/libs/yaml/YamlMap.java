package org.eu.pcraft.powerfulfireworks.libs.yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * YAML 文本 → 保序 {@code Map<String, Object>} 的只读门面（纯 JDK，零 Bukkit 依赖）。
 *
 * <p>解析语义：SafeConstructor、禁 timestamp、重复键抛异常、空文档归一空 Map、多文档取首文档、
 * BOM 剥离；安全与上限保持 snakeyaml 默认。</p>
 *
 * <p>与写回用文本合并器 {@code YamlMerge} 共享同一引擎内核，仅 processComments 开关不同。</p>
 */
public final class YamlMap {

    private YamlMap() {}

    private static final Yaml PARSER = createParser();

    private static Yaml createParser() {
        LoaderOptions options = new LoaderOptions();
        // S8：重复键默认报错（snakeyaml 2.6 默认放行后者覆盖，显式收紧）。
        options.setAllowDuplicateKeys(false);
        Resolver resolver = new Resolver() {
            @Override
            public Tag resolve(NodeId kind, String value, boolean implicit) {
                Tag tag = super.resolve(kind, value, implicit);
                // S3：禁 timestamp——日期样标量一律按字符串，防隐式变 Date。
                return tag == Tag.TIMESTAMP ? Tag.STR : tag;
            }
        };
        // 五个构造参数共享同一个 LoaderOptions，保证安全/上限策略单一来源。
        return new Yaml(
                new SafeConstructor(options),
                new Representer(new DumperOptions()),
                new DumperOptions(),
                options,
                resolver);
    }

    /**
     * 解析 YAML 文本为保序 Map；空文档/纯注释 → 空 Map；重复键/语法错/顶层非映射 →
     * {@link YamlParseException}。参数为 {@code null} 抛 {@link IllegalArgumentException}。
     */
    public static Map<String, Object> parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        final String input = stripBom(text);
        try {
            Object loaded = PARSER.load(input);
            if (loaded == null) {
                // S6：空文档/纯注释（snakeyaml 解析为 null）→ 空 Map
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map<?, ?> m)) {
                // S7：顶层必须是映射
                throw new YamlParseException(
                        -1, -1, "顶层必须是映射，实际为 " + loaded.getClass().getSimpleName(), null);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        } catch (YamlParseException e) {
            throw e;
        } catch (YAMLException e) {
            throw toYamlParseException(e);
        }
    }

    /** S13：剥离 UTF-8 BOM（否则 \uFEFF 会粘进首个键）。 */
    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** S14：MarkedYAMLException → 结构化异常（行列 1-based；无位置为 -1）。 */
    private static YamlParseException toYamlParseException(YAMLException e) {
        MarkedYAMLException marked = e instanceof MarkedYAMLException my ? my : null;
        Mark mark = marked != null ? marked.getProblemMark() : null;
        int line = mark != null ? mark.getLine() + 1 : -1;
        int column = mark != null ? mark.getColumn() + 1 : -1;
        String problem =
                marked != null && marked.getProblem() != null ? marked.getProblem() : String.valueOf(e.getMessage());
        return new YamlParseException(line, column, problem, e);
    }
}
