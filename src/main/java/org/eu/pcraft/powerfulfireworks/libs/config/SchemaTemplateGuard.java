package org.eu.pcraft.powerfulfireworks.libs.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMap;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlParseException;

/** 模板一致性守卫（package-private）：schema 声明 vs 默认模板文本——未知键/缺声明键/解析错。 */
final class SchemaTemplateGuard {

    private SchemaTemplateGuard() {}

    static List<ConfigIssue> check(ConfigSchema schema, String templateText) {
        Map<String, Object> tpl;
        try {
            tpl = YamlMap.parse(templateText);
        } catch (YamlParseException e) {
            String problem = e.problem() == null || e.problem().isBlank() ? "模板解析失败" : e.problem();
            return List.of(new ConfigIssue(IssueLevel.ERROR, "", problem, "修正模板语法"));
        }
        List<ConfigIssue> out = new ArrayList<>();
        Set<String> known = schema.knownKeys();
        for (String key : known) {
            if (ConfigSchema.rawAt(tpl, key) == null) {
                out.add(new ConfigIssue(IssueLevel.WARN, key, "声明的键不在模板: " + key, "补全模板"));
            }
        }
        for (String tKey : tpl.keySet()) {
            boolean declared = known.contains(tKey);
            if (!declared) {
                for (String k : known) {
                    if (k.startsWith(tKey + ".")) {
                        declared = true;
                        break;
                    }
                }
            }
            if (!declared) {
                out.add(new ConfigIssue(IssueLevel.WARN, tKey, "模板含未声明键: " + tKey, "删除或声明该键"));
            }
        }
        return List.copyOf(out);
    }
}
