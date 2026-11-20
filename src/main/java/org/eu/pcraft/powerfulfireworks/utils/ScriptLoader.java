package org.eu.pcraft.powerfulfireworks.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eu.pcraft.powerfulfireworks.config.DiagnosticReporter;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;
import org.eu.pcraft.powerfulfireworks.script.CompiledScript;
import org.slf4j.Logger;

/**
 * 装载 {@code scripts/} 下的脚本，并在目录里一个脚本都没有时释放 jar 自带的示例。
 *
 * <p>单个脚本编译失败只记一条诊断并跳过，不影响其它脚本，也不阻止插件启动——
 * 脚本是可选内容，坏一个不该让服务器起不来。
 */
public final class ScriptLoader {

    private static final String EXTENSION = ".js";
    private static final String EXAMPLE = "example.js";
    private static final String SOURCE = "scripts";

    private ScriptLoader() {
    }

    /**
     * @param bundled 插件自带脚本的读取器（按文件名取字节，没有则返回 {@code null}）；
     *                目录里没有脚本时用它释放示例
     * @param dryRun  只读校验：不建目录、不写示例，只把问题记进 {@code reporter}
     */
    public static Map<String, CompiledScript> load(Path scriptsDir, Function<String, byte[]> bundled,
                                                   Logger logger, DiagnosticReporter reporter, boolean dryRun) {
        Map<String, CompiledScript> loaded = new TreeMap<>();

        List<Path> files;
        try {
            if (!Files.isDirectory(scriptsDir)) {
                if (dryRun) {
                    return loaded;
                }
                Files.createDirectories(scriptsDir);
            }
            files = listScripts(scriptsDir);
        } catch (IOException e) {
            reporter.add(SOURCE, List.of(new ConfigIssue(IssueLevel.ERROR, "",
                    "无法读取脚本目录：" + e.getMessage(),
                    "确认目录存在且可读")));
            return loaded;
        }

        if (files.isEmpty()) {
            // 没有脚本时放一份示例，省掉"必须先看文档才知道写什么"的门槛
            if (!dryRun) {
                installExample(scriptsDir, bundled, logger, reporter);
                try {
                    files = listScripts(scriptsDir);
                } catch (IOException e) {
                    reporter.add(SOURCE, List.of(new ConfigIssue(IssueLevel.WARN, "",
                            "写完示例脚本后仍无法读取脚本目录：" + e.getMessage(),
                            "确认目录权限")));
                }
            }
        }

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - EXTENSION.length());
            try {
                loaded.put(id, CompiledScript.compile(id, Files.readString(file, StandardCharsets.UTF_8)));
            } catch (CompiledScript.CompileException e) {
                reporter.add(SOURCE, List.of(new ConfigIssue(IssueLevel.WARN, fileName,
                        "脚本编译失败，不会运行：" + e.getMessage(),
                        "检查语法与入口定义")));
            } catch (IOException e) {
                reporter.add(SOURCE, List.of(new ConfigIssue(IssueLevel.WARN, fileName,
                        "脚本读取失败，不会运行：" + e.getMessage(),
                        "确认文件权限")));
            }
        }

        if (!dryRun) {
            logger.info("Loaded {} script(s)", loaded.size());
        }
        return loaded;
    }

    private static List<Path> listScripts(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>();
            for (Path path : stream.toList()) {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION)) {
                    files.add(path);
                }
            }
            files.sort(null);
            return files;
        }
    }

    private static void installExample(Path dir, Function<String, byte[]> bundled,
                                       Logger logger, DiagnosticReporter reporter) {
        byte[] data = bundled == null ? null : bundled.apply(EXAMPLE);
        if (data == null) {
            reporter.add(SOURCE, List.of(new ConfigIssue(IssueLevel.WARN, EXAMPLE,
                    "jar 里没有示例脚本 " + EXAMPLE,
                    "重新打包插件")));
            return;
        }
        Path target = dir.resolve(EXAMPLE);
        try {
            Files.write(target, data);
            logger.info("已写出示例脚本 {}", target.getFileName());
        } catch (IOException e) {
            reporter.add(SOURCE, List.of(new ConfigIssue(IssueLevel.WARN, EXAMPLE,
                    "无法写出示例脚本：" + e.getMessage(),
                    "确认目录可写")));
        }
    }
}
