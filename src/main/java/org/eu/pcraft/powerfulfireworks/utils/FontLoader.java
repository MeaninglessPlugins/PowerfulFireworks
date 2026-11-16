package org.eu.pcraft.powerfulfireworks.utils;

import org.eu.pcraft.powerfulfireworks.config.DiagnosticReporter;
import org.eu.pcraft.powerfulfireworks.config.PepperConfigModule;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigIssue;
import org.eu.pcraft.powerfulfireworks.libs.config.IssueLevel;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 字体装载与文本烟花字体的校验。
 *
 * <p>字体文件缺失或损坏算警告而不是错误：文本烟花会退回普通烟花，插件本身还能正常跑。</p>
 */
public final class FontLoader {

    private FontLoader() {
    }

    /**
     * 按 {@code fonts} 段装载字体。
     *
     * @param brokenFonts 装载失败（文件缺失/解析失败）的字体 ID（小写），供后续校验去重
     * @return 字体 ID（小写）到字体的映射，只有装载成功的才会出现
     */
    public static Map<String, BitmapFont> load(Path dataPath, Map<String, String> configured, Logger logger,
                                               DiagnosticReporter reporter, boolean dryRun,
                                               Set<String> brokenFonts) {
        return load(dataPath, configured, logger, reporter, dryRun, brokenFonts, null);
    }

    /**
     * @param bundled 插件自带字体的读取器（按文件名取字节，没有则返回 {@code null}）；
     *                配置里写了文件名、数据目录里却没有时，会先把它释放出来再用
     */
    public static Map<String, BitmapFont> load(Path dataPath, Map<String, String> configured, Logger logger,
                                               DiagnosticReporter reporter, boolean dryRun,
                                               Set<String> brokenFonts, Function<String, byte[]> bundled) {
        Path folder = dataPath.resolve("fonts");
        Map<String, BitmapFont> loaded = new HashMap<>();

        if (!Files.exists(folder)) {
            if (dryRun) {
                return loaded;
            }
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                throw new RuntimeException("Unable to initialize fonts directory", e);
            }
        }

        for (Map.Entry<String, String> entry : configured.entrySet()) {
            loadSingle(folder, entry.getKey(), entry.getValue(), loaded, logger, reporter, dryRun, brokenFonts, bundled);
        }
        return loaded;
    }

    private static void loadSingle(Path folder, String fontId, String fileName, Map<String, BitmapFont> loaded,
                                   Logger logger, DiagnosticReporter reporter, boolean dryRun,
                                   Set<String> brokenFonts, Function<String, byte[]> bundled) {
        Path path = folder.resolve(fileName);
        try {
            byte[] data = Files.isRegularFile(path) ? Files.readAllBytes(path) : extractBundled(path, fileName, bundled, dryRun, logger);
            if (data != null) {
                BitmapFont font = BitmapFont.parseBDF(new String(data, StandardCharsets.UTF_8));
                if (font.getGlyphCount() == 0) {
                    throw new IllegalArgumentException("文件里没有任何字符");
                }
                loaded.put(fontId.toLowerCase(Locale.ROOT), font);
                if (!dryRun) {
                    logger.info("Loaded font {} from {}", fontId, path);
                }
            } else {
                brokenFonts.add(fontId.toLowerCase(Locale.ROOT));
                reporter.add("fonts", List.of(new ConfigIssue(IssueLevel.WARN, fontId,
                        "字体文件 " + fileName + " 不存在，文本烟花会退回普通烟花",
                        "把 BDF 文件放进 fonts 文件夹")));
            }
        } catch (Throwable t) {
            brokenFonts.add(fontId.toLowerCase(Locale.ROOT));
            reporter.add("fonts", List.of(new ConfigIssue(IssueLevel.WARN, fontId,
                    "字体文件 " + fileName + " 解析失败，文本烟花会退回普通烟花：" + t.getMessage(),
                    "确认是 BDF 格式")));
        }
    }

    private static byte[] extractBundled(Path path, String fileName, Function<String, byte[]> bundled,
                                         boolean dryRun, Logger logger) {
        if (bundled == null) {
            return null;
        }
        byte[] data = bundled.apply(fileName);
        if (data == null) {
            return null;
        }
        if (!dryRun) {
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, data);
                logger.info("Extracted bundled font {} to {}", fileName, path);
            } catch (IOException e) {
                logger.warn("Unable to extract bundled font {} to {}", fileName, path);
                return null;
            }
        }
        return data;
    }

    /**
     * 文本烟花引用的字体必须写在 {@code fonts} 段里；文件本身加载失败的情况已经由
     * {@link #load} 报过警告，这里不重复报。
     */
    public static void checkTextFont(PepperConfigModule config, Map<String, BitmapFont> fonts,
                                     Set<String> brokenFonts, DiagnosticReporter reporter) {
        PepperConfigModule.RandomFirework random = config.randomFirework;
        if (random == null || random.text == null || !random.enabled || !random.text.enabled) {
            return;
        }
        String font = random.text.font;
        if (font == null || font.isBlank()) {
            return;
        }
        if (!config.fonts.containsKey(font)) {
            reporter.add("config.yml", List.of(new ConfigIssue(IssueLevel.ERROR, "randomFirework.text.font",
                    "字体 \"" + font + "\" 不在 fonts 段里，文本烟花会退回普通烟花",
                    "在 fonts 段加一行，或者把这里改成已有的字体 ID")));
        } else if (brokenFonts.contains(font.toLowerCase(Locale.ROOT))) {
            // 文件缺失/损坏已经在 fonts 那一组报过
        } else if (!fonts.containsKey(font.toLowerCase(Locale.ROOT))) {
            reporter.add("config.yml", List.of(new ConfigIssue(IssueLevel.ERROR, "randomFirework.text.font",
                    "字体 \"" + font + "\" 没有加载成功，文本烟花会退回普通烟花",
                    "检查 fonts 段与该文件")));
        }
    }
}
