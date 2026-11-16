package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eu.pcraft.powerfulfireworks.config.DiagnosticReporter;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * fireworks/*.yml 的装载与校验。
 *
 * <p>与 config.yml 的区别：这里的结构不是 {@code @ConfigModel}，所以规则直接看原始 section。
 * 规则同样只读：编译失败的文件会被跳过，但已经编译出来的演出不会被规则改动。</p>
 *
 * <p>跨文件规则（id 重复、引用目标存在）在全部文件编译完之后统一执行。</p>
 */
public final class SchedulerValidator {

    /** 与 {@link org.eu.pcraft.powerfulfireworks.config.ValidationRules#VISIBLE_TICKS} 保持一致。 */
    private static final int VISIBLE_TICKS = 56;

    private static final int MAX_COUNT = 50;

    private static final Set<String> COMMON_KEYS =
            Set.of("type", "flyTime", "presets", "launchChance", "explosionChance");

    private static final Map<String, Set<String>> TYPE_KEYS = Map.of(
            "original", Set.of("count", "xOff", "yOff", "zOff"),
            "text", Set.of("rotate", "size", "font", "text", "gap", "face"),
            "reference", Set.of("target", "blocking"),
            "wait", Set.of("ticks"));

    private SchedulerValidator() {
    }

    /**
     * 读取目录下的全部演出并校验。
     *
     * @param fontIds 已成功加载的字体 ID（小写）
     * @return 编译成功的演出，按文件顺序；有问题的文件不会出现在返回值里
     */
    public static Map<String, FireworkScheduler> load(Path folder, Set<String> fontIds, DiagnosticReporter reporter) {
        Map<String, FireworkScheduler> schedulers = new LinkedHashMap<>();
        Map<String, String> owners = new HashMap<>();

        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> paths = Files.list(folder)) {
                paths.filter(SchedulerValidator::isYamlFile).sorted().forEach(files::add);
            } catch (IOException e) {
                reporter.add(sourceOf(folder), List.of(new ConfigIssue(IssueLevel.ERROR, "",
                        "无法读取 fireworks 目录：" + e.getMessage(), "检查目录权限")));
                return schedulers;
            }
        }

        for (Path file : files) {
            String source = sourceOf(file);
            List<ConfigIssue> issues = new ArrayList<>();
            ConfigurationSection section = read(file, issues);
            if (section == null) {
                reporter.add(source, issues);
                continue;
            }

            String id = section.getString("id");
            if (id == null || id.isBlank()) {
                error(issues, "id", "缺少 id，文件不会加载", "给这场演出起一个唯一的名字");
                reporter.add(source, issues);
                continue;
            }
            String duplicated = owners.get(id);
            if (duplicated != null) {
                error(issues, "id", "id \"" + id + "\" 与 " + duplicated + " 重复，本文件已跳过",
                        "改一个不重复的 id");
                reporter.add(source, issues);
                continue;
            }

            validateSection(section, id, fontIds, issues);

            FireworkScheduler compiled = null;
            try {
                compiled = FireworkScheduler.compile(section);
            } catch (Throwable t) {
                if (!hasError(issues)) {
                    error(issues, "", "编译失败：" + rootMessage(t), "按上一条提示修正后重试");
                }
            }

            if (compiled != null) {
                schedulers.put(id, compiled);
                owners.put(id, file.getFileName().toString());
            }
            reporter.add(source, issues);
        }

        validateReferences(schedulers, owners, reporter);
        return schedulers;
    }

    /** 跨文件：引用目标必须存在于本次加载的演出里。 */
    private static void validateReferences(Map<String, FireworkScheduler> schedulers,
                                           Map<String, String> owners,
                                           DiagnosticReporter reporter) {
        Set<String> ids = schedulers.keySet();
        for (Map.Entry<String, FireworkScheduler> entry : schedulers.entrySet()) {
            for (String target : entry.getValue().getReferenceTargets()) {
                if (!ids.contains(target)) {
                    reporter.add("fireworks/" + owners.get(entry.getKey()), List.of(new ConfigIssue(
                            IssueLevel.ERROR, "nodes.reference.target",
                            "引用的演出 \"" + target + "\" 不存在，执行到该节点时会被跳过",
                            "检查 id 拼写，或确认被引用的文件没有被跳过")));
                }
            }
        }
    }

    private static void validateSection(ConfigurationSection section, String id,
                                        Set<String> fontIds, List<ConfigIssue> issues) {
        if (!section.isConfigurationSection("presets")) {
            error(issues, "presets", "缺少 presets 段，文件不会加载", "没有预设时写 presets: {}");
        }
        Set<String> presetIds = section.isConfigurationSection("presets")
                ? section.getConfigurationSection("presets").getKeys(false)
                : Set.of();

        Object rawNodes = section.get("nodes");
        if (!(rawNodes instanceof List<?> nodes)) {
            error(issues, "nodes", "缺少 nodes 列表，文件不会加载", "没有节点时写 nodes: []");
            return;
        }

        for (int i = 0; i < nodes.size(); i++) {
            String path = "nodes[" + i + "]";
            if (!(nodes.get(i) instanceof Map<?, ?> node)) {
                error(issues, path, "节点必须是一个映射", "例如 { type: wait, ticks: 20 }");
                continue;
            }
            validateNode(node, path, id, presetIds, fontIds, issues);
        }
    }

    private static void validateNode(Map<?, ?> node, String path, String schedulerId,
                                     Set<String> presetIds, Set<String> fontIds, List<ConfigIssue> issues) {
        Object rawType = node.get("type");
        if (rawType == null) {
            error(issues, path + ".type", "缺少 type", "reference / original / text / wait");
            return;
        }
        String type = String.valueOf(rawType);
        Set<String> typeKeys = TYPE_KEYS.get(type);
        if (typeKeys == null) {
            error(issues, path + ".type", "未知的节点类型 \"" + type + "\"", "reference / original / text / wait");
            return;
        }

        for (Object key : node.keySet()) {
            String name = String.valueOf(key);
            if (!COMMON_KEYS.contains(name) && !typeKeys.contains(name)) {
                warn(issues, path + "." + name, "未知的键，会被忽略", "检查拼写");
            }
        }

        checkFlyTime(node.get("flyTime"), path + ".flyTime", issues);
        checkChance(node.get("launchChance"), path + ".launchChance", "发射声", issues);
        checkChance(node.get("explosionChance"), path + ".explosionChance", "爆炸声", issues);
        checkPresets(node.get("presets"), path + ".presets", presetIds, issues);

        switch (type) {
            case "original" -> validateOriginal(node, path, issues);
            case "text" -> validateText(node, path, fontIds, issues);
            case "reference" -> validateReference(node, path, schedulerId, issues);
            case "wait" -> validateWait(node, path, issues);
            default -> {
            }
        }
    }

    private static void validateOriginal(Map<?, ?> node, String path, List<ConfigIssue> issues) {
        Double count = number(node.get("count"));
        if (node.get("count") != null && count == null) {
            warn(issues, path + ".count", "应为数字，节点会当作 1 处理", "例如 3");
        } else if (count != null) {
            if (count <= 0) {
                error(issues, path + ".count", "数量必须大于 0，否则节点什么都不做", "改成 1 或更大");
            } else if (count > MAX_COUNT) {
                warn(issues, path + ".count", "数量 " + count.intValue() + " 偏大，单个节点会一次性发出大量烟花",
                        "建议不超过 " + MAX_COUNT);
            }
        }
        for (String key : List.of("xOff", "yOff", "zOff")) {
            Object value = node.get(key);
            if (value != null && !isIntervalLike(value)) {
                error(issues, path + "." + key, "偏移量需要数字、[最小值, 最大值] 或 {minimum, maximum}",
                        "例如 [0, 10]");
            }
        }
    }

    private static void validateText(Map<?, ?> node, String path, Set<String> fontIds, List<ConfigIssue> issues) {
        Object font = node.get("font");
        if (!(font instanceof String fontId) || fontId.isBlank()) {
            error(issues, path + ".font", "缺少字体 ID", "填写 fonts 段里的一个键");
        } else if (!fontIds.contains(fontId.toLowerCase(Locale.ROOT))) {
            error(issues, path + ".font", "字体 \"" + fontId + "\" 没有成功加载", "检查 fonts 段与字体文件");
        }

        Object text = node.get("text");
        if (!(text instanceof String content) || content.isEmpty()) {
            warn(issues, path + ".text", "文本为空，节点不会生成任何点阵", "填写要显示的文字");
        }

        Double size = number(node.get("size"));
        if (size != null && size <= 0) {
            error(issues, path + ".size", "点阵间距必须大于 0，否则所有点会重合", "改成大于 0 的数值");
        }
        Double gap = number(node.get("gap"));
        if (gap != null && gap < 0) {
            error(issues, path + ".gap", "字符间隔不能为负", "改成 0 或更大");
        }

        Object rotate = node.get("rotate");
        if (rotate != null && !(rotate instanceof Boolean)) {
            warn(issues, path + ".rotate", "应为 true 或 false，节点会当作 false 处理", "例如 true");
        }
        boolean rotating = Boolean.TRUE.equals(rotate);
        Object face = node.get("face");
        if (!rotating) {
            if (face == null) {
                error(issues, path + ".face", "没有开启 rotate 时必须指定水平朝向",
                        "EAST / WEST / NORTH / SOUTH");
            } else if (!Set.of("EAST", "WEST", "NORTH", "SOUTH").contains(String.valueOf(face).toUpperCase(Locale.ROOT))) {
                error(issues, path + ".face", "朝向 \"" + face + "\" 不是水平方向，点阵会叠成一条竖线",
                        "EAST / WEST / NORTH / SOUTH");
            }
        }
    }

    private static void validateReference(Map<?, ?> node, String path, String schedulerId, List<ConfigIssue> issues) {
        Object target = node.get("target");
        if (!(target instanceof String id) || id.isBlank()) {
            error(issues, path + ".target", "缺少引用目标", "填写另一场演出的 id");
        } else if (id.equals(schedulerId)) {
            error(issues, path + ".target", "引用自己会造成无限递归", "改成别的演出");
        }
        Object blocking = node.get("blocking");
        if (blocking != null && !(blocking instanceof Boolean)) {
            warn(issues, path + ".blocking", "应为 true 或 false，节点会当作 false 处理", "例如 true");
        }
    }

    private static void validateWait(Map<?, ?> node, String path, List<ConfigIssue> issues) {
        Object raw = node.get("ticks");
        if (raw == null) {
            return;
        }
        Double ticks = number(raw);
        if (ticks == null) {
            warn(issues, path + ".ticks", "应为数字，节点会当作 1 tick 处理", "例如 20");
        } else if (ticks < 0) {
            error(issues, path + ".ticks", "等待时间不能为负", "例如 20");
        } else if (ticks == 0) {
            warn(issues, path + ".ticks", "等待 0 tick 没有意义，节点可以直接删掉", "删掉这个节点");
        }
    }

    private static void checkFlyTime(Object raw, String path, List<ConfigIssue> issues) {
        if (raw == null) {
            return;
        }
        Double flyTime = number(raw);
        if (flyTime == null) {
            warn(issues, path, "应为数字，节点会当作 0 tick 处理", "例如 40");
            return;
        }
        if (flyTime < 0) {
            error(issues, path, "升空时间不能为负", "例如 40");
        } else if (flyTime > VISIBLE_TICKS) {
            warn(issues, path, flyTime.intValue() + " tick 超出渲染范围：火箭在 56 tick 后已升到 64 格外，"
                    + "引爆点看不到", "建议不超过 " + VISIBLE_TICKS);
        }
    }

    private static void checkChance(Object raw, String path, String what, List<ConfigIssue> issues) {
        if (raw == null) {
            return;
        }
        Double chance = number(raw);
        if (chance == null) {
            warn(issues, path, "应为 0 到 1 之间的数字，节点会当作 0 处理", "例如 0.2");
            return;
        }
        if (chance < 0 || chance > 1) {
            String effect = chance > 1 ? "始终不播放" : "始终播放";
            error(issues, path, what + "的播放概率 " + chance + " 不在 0..1，实际表现为" + effect,
                    "改成 0 到 1 之间");
        }
    }

    private static void checkPresets(Object raw, String path, Set<String> presetIds, List<ConfigIssue> issues) {
        if (raw == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        if (raw instanceof String single) {
            names.add(single);
        } else if (raw instanceof List<?> list) {
            for (Object element : list) {
                names.add(String.valueOf(element));
            }
        } else {
            error(issues, path, "需要预设名字或名字列表", "例如 [red, blue]");
            return;
        }
        for (String name : names) {
            if (!presetIds.contains(name)) {
                error(issues, path, "预设 \"" + name + "\" 没有在 presets 段里定义", "补上预设或改名");
            }
        }
    }

    private static boolean isIntervalLike(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof List<?> list) {
            return list.size() >= 2 && list.get(0) instanceof Number && list.get(1) instanceof Number;
        }
        if (value instanceof Map<?, ?> map) {
            return map.get("minimum") instanceof Number && map.get("maximum") instanceof Number;
        }
        return false;
    }

    private static Double number(Object raw) {
        return raw instanceof Number n ? n.doubleValue() : null;
    }

    private static ConfigurationSection read(Path file, List<ConfigIssue> issues) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Throwable t) {
            error(issues, "", "无法解析文件：" + rootMessage(t), "检查 YAML 缩进与语法");
            return null;
        }
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString();
        return Files.isRegularFile(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private static String sourceOf(Path path) {
        Path name = path.getFileName();
        return "fireworks/" + (name == null ? path.toString() : name.toString());
    }

    private static boolean hasError(List<ConfigIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.level() == IssueLevel.ERROR);
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static void warn(List<ConfigIssue> issues, String path, String message, String hint) {
        issues.add(new ConfigIssue(IssueLevel.WARN, path, message, hint));
    }

    private static void error(List<ConfigIssue> issues, String path, String message, String hint) {
        issues.add(new ConfigIssue(IssueLevel.ERROR, path, message, hint));
    }
}
