package org.eu.pcraft.powerfulfireworks.libs.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMap;
import org.eu.pcraft.powerfulfireworks.libs.yaml.YamlMerge;

/**
 * 单配置文件运行时存储（公共 API）：首跑材质化默认文件 → 注释感知文档 + 类型化模型；支持
 * 运行时条目值/{@link ConfigDoc#withComments 注释}修改、原子保存、reload（失败保留旧快照）、
 * 升级补键。
 *
 * <p>写回策略：{@link #save()} 只落当前内存文档；{@link #upgrade} 走
 * 注释保留合并（putIfAbsent + 备份）。运行期其它操作不自动触盘。数据文件不纳入。</p>
 */
public final class ConfigFileStore<T> {

    private final Class<T> model;
    private final Path file;
    private final String resourcePath;
    private final ConfigPostLoad<T> postLoad;
    private final ConfigMigration migration;
    private volatile Current<T> current;

    private record Current<T>(
            ConfigDoc doc, T typed, ConfigValues values, boolean migrated, List<ConfigIssue> issues) {}

    private ConfigFileStore(
            Class<T> model,
            Path dataFolder,
            String fileName,
            String resourcePath,
            Current<T> initial,
            ConfigPostLoad<T> postLoad,
            ConfigMigration migration) {
        this.model = model;
        this.file = dataFolder.resolve(fileName);
        this.resourcePath = resourcePath;
        this.postLoad = postLoad;
        this.migration = migration;
        this.current = initial;
    }

    /** 装载：目标文件缺失时从 classpath 资源 copy-once（带注释默认文件），再解析绑定。 */
    public static <T> ConfigFileStore<T> load(
            Class<T> model, Path dataFolder, String resourcePath, ClassLoader loader) {
        return load(model, dataFolder, resourcePath, loader, null, null);
    }

    /**
     * 装载 + 装载后处理钩子：与 {@link Bindings#load(Class, Map, IssueCollector, ConfigPostLoad)}
     * 同语义；store 的 reload / set 重绑定同样触发 post-load。
     */
    public static <T> ConfigFileStore<T> load(
            Class<T> model, Path dataFolder, String resourcePath, ClassLoader loader, ConfigPostLoad<T> postLoad) {
        return load(model, dataFolder, resourcePath, loader, postLoad, null);
    }

    /** 装载 + 可插拔迁移服务（对齐 ConfigMe MigrationService；保守：只改内存不自动落盘）。 */
    public static <T> ConfigFileStore<T> load(
            Class<T> model, Path dataFolder, String resourcePath, ClassLoader loader, ConfigMigration migration) {
        return load(model, dataFolder, resourcePath, loader, null, migration);
    }

    /**
     * 装载 + 装载后处理钩子 + 可插拔迁移服务：迁移在内存 root 副本上执行并塑造类型化模型；
     * 是否迁移见 {@link #migrated()}，落盘由调用方显式 {@link #save()} / {@link #upgrade(int)}。
     */
    public static <T> ConfigFileStore<T> load(
            Class<T> model,
            Path dataFolder,
            String resourcePath,
            ClassLoader loader,
            ConfigPostLoad<T> postLoad,
            ConfigMigration migration) {
        return load(model, dataFolder, resourcePath, resourcePath, loader, postLoad, migration);
    }

    /** 装载（双路径）：数据目录文件名与 classpath 默认资源路径解耦。 */
    public static <T> ConfigFileStore<T> load(
            Class<T> model, Path dataFolder, String fileName, String resourcePath, ClassLoader loader) {
        return load(model, dataFolder, fileName, resourcePath, loader, null, null);
    }

    /** 装载 + 装载后处理钩子（双路径）。 */
    public static <T> ConfigFileStore<T> load(
            Class<T> model,
            Path dataFolder,
            String fileName,
            String resourcePath,
            ClassLoader loader,
            ConfigPostLoad<T> postLoad) {
        return load(model, dataFolder, fileName, resourcePath, loader, postLoad, null);
    }

    /**
     * 装载（双路径全参）：{@code fileName} 是数据目录下的相对路径（运维可见），
     * {@code resourcePath} 是 jar 内默认资源路径（可位于子目录，如 {@code defaults/config.yml}）。
     * 首跑从资源 copy-once 到 {@code fileName}，之后只读磁盘。
     */
    public static <T> ConfigFileStore<T> load(
            Class<T> model,
            Path dataFolder,
            String fileName,
            String resourcePath,
            ClassLoader loader,
            ConfigPostLoad<T> postLoad,
            ConfigMigration migration) {
        try {
            ConfigFile.copyDefaultIfMissing(dataFolder, fileName, loader, resourcePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("bundled default resource missing or unreadable: " + resourcePath, e);
        }
        Path file = dataFolder.resolve(fileName);
        Current<T> initial;
        try {
            initial = read(file, model, postLoad, migration);
        } catch (RuntimeException | IOException e) {
            // 首跑即损坏/IO 错：回落到**出厂默认资源**（注释与取值都逐字保留，含集合/映射默认；
            // 合成 defaultsText 对 List<record>/Map<String,record> 之类默认只能吐出 toString 文本）
            // + ERROR 通告；不写盘，避免覆盖用户文件。
            String defaults = resourceText(loader, resourcePath);
            if (defaults == null) {
                defaults = Bindings.defaultsText(model);
            }
            ConfigDoc doc = ConfigDoc.parse(defaults);
            IssueCollector issues = new IssueCollector();
            issues.add(new ConfigIssue(IssueLevel.ERROR, "", "配置加载失败，使用内置默认: " + e.getMessage(), "检查配置文件"));
            Map<String, Object> fallbackRoot;
            try {
                fallbackRoot = YamlMap.parse(defaults);
            } catch (RuntimeException parseFailure) {
                // 出厂资源自身不可解析（打包事故）：退到注解默认文本，仍不让插件起不来
                defaults = Bindings.defaultsText(model);
                doc = ConfigDoc.parse(defaults);
                fallbackRoot = YamlMap.parse(defaults);
            }
            Bindings.LoadResult<T> lr = Bindings.loadWithValues(model, fallbackRoot, new IssueCollector(), postLoad);
            initial = new Current<>(doc, lr.model(), lr.values(), false, issues.issues());
        }
        return new ConfigFileStore<>(model, dataFolder, fileName, resourcePath, initial, postLoad, migration);
    }

    /** 读 classpath 默认资源文本（缺失/IO 错返回 null，交由调用方退到注解默认文本）。 */
    private static String resourceText(ClassLoader loader, String resourcePath) {
        try (java.io.InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (IOException e) {
            return null;
        }
    }

    private static <T> Current<T> read(Path file, Class<T> model, ConfigPostLoad<T> postLoad, ConfigMigration migration)
            throws IOException {
        String text = ConfigFile.readUtf8(file);
        ConfigDoc doc = ConfigDoc.parse(text);
        Map<String, Object> root = YamlMap.parse(text);
        boolean migrated = false;
        if (migration != null) {
            // 迁移决策所需的迁移前值级信号（独立 pass，不污染主 issues）。
            IssueCollector pre = new IssueCollector();
            ConfigValues preValues = Bindings.loadWithValues(model, root, pre).values();
            // 独立深副本：迁移对 root 的任意嵌套修改都不影响迁移前的 root 视图
            // （约定迁移只应改内存工作副本，返回 true 才替换绑定源）。
            Map<String, Object> work = YamlMap.parse(text);
            migrated = migration.checkAndMigrate(work, preValues);
            if (migrated) {
                root = work;
            }
        }
        IssueCollector issues = new IssueCollector();
        Bindings.LoadResult<T> lr = Bindings.loadWithValues(model, root, issues, postLoad);
        return new Current<>(doc, lr.model(), lr.values(), migrated, issues.issues());
    }

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    /** 当前类型化模型快照（volatile；失败重载后仍为旧值）。 */
    public T get() {
        return this.current.typed();
    }

    /** 当前装载的值级合法性信号（PRESENT/MISSING/INVALID；与 {@link #get()} 同快照）。 */
    public ConfigValues values() {
        return this.current.values();
    }

    /** 最近一次装载/重载是否经 {@link ConfigMigration} 发生迁移（模型与磁盘分叉；落盘由调用方显式裁决）。 */
    public boolean migrated() {
        return this.current.migrated();
    }

    /** 当前文档全文（含最新编辑，未 save 前仅内存）。 */
    public String text() {
        return this.current.doc().text();
    }

    /** 数据目录下的目标文件路径（相对）。 */
    public String fileName() {
        return this.file.getFileName().toString();
    }

    /** jar 内默认资源路径（可能与 {@link #fileName()} 不同，如 {@code defaults/config.yml}）。 */
    public String resourcePath() {
        return this.resourcePath;
    }

    /** 最近一次装载/修改后的诊断问题（含绑定校验 WARN/ERROR）。 */
    public List<ConfigIssue> lastIssues() {
        return this.current.issues();
    }

    public boolean contains(String path) {
        return this.current.doc().contains(path);
    }

    public Object value(String path) {
        return this.current.doc().value(path);
    }

    public List<String> blockComments(String path) {
        return this.current.doc().blockComments(path);
    }

    public String inlineComment(String path) {
        return this.current.doc().inlineComment(path);
    }

    // ------------------------------------------------------------------
    // 修改（内存；save 落盘）
    // ------------------------------------------------------------------

    /** 设置条目值（标量发射器编码 → 字节保真改写 → 即时重绑定模型）。 */
    public void set(String path, Object value) {
        ConfigDoc doc = this.current.doc().withValue(path, value);
        commitDoc(doc);
    }

    /** 替换条目注释（块注释行不写 "#" 前缀；inline null 删除）。 */
    public void setComments(String path, List<String> block, String inline) {
        ConfigDoc doc = this.current.doc().withComments(path, block, inline);
        commitDoc(doc);
    }

    private void commitDoc(ConfigDoc doc) {
        IssueCollector issues = new IssueCollector();
        Bindings.LoadResult<T> lr = Bindings.loadWithValues(this.model, parseMap(doc.text()), issues, this.postLoad);
        this.current = new Current<>(doc, lr.model(), lr.values(), false, issues.issues());
    }

    // ------------------------------------------------------------------
    // 落盘 / 重载 / 迁移
    // ------------------------------------------------------------------

    /** 原子写当前文档到磁盘（temp + rename）。 */
    public void save() throws IOException {
        ConfigFile.writeAtomic(this.file, this.current.doc().text());
    }

    /** 重载磁盘：解析失败保留旧快照并记 ERROR，旧配置绝不静默降级为默认。 */
    public void reload() {
        try {
            this.current = read(this.file, this.model, this.postLoad, this.migration);
        } catch (IOException | RuntimeException e) {
            List<ConfigIssue> issues = new ArrayList<>(this.current.issues());
            issues.add(new ConfigIssue(IssueLevel.ERROR, "", "配置重载失败，保留上次成功加载的配置: " + e.getMessage(), "检查配置文件"));
            this.current = new Current<>(
                    this.current.doc(), this.current.typed(), this.current.values(), false, List.copyOf(issues));
        }
    }

    /**
     * 升级补键（需显式调用）：磁盘 vs 模型默认模板 → 只补缺失键块（含注释），
     * 已有键值一律不动；补入前备份 {@code config.yml.bak-v<旧版本>}。成功后重载。
     */
    public void upgrade(int schemaVersion) throws IOException {
        if (!Files.exists(this.file)) {
            return;
        }
        String disk = ConfigFile.readUtf8(this.file);
        String tpl = Bindings.defaultsText(this.model);
        YamlMerge.Result result = UpgradePatch.apply(disk, tpl);
        if (!result.changed()) {
            return;
        }
        int oldVersion = ConfigVersions.versionOf(YamlMap.parse(disk));
        if (oldVersion < schemaVersion) {
            Path backup = this.file.resolveSibling(this.file.getFileName() + ".bak-v" + oldVersion);
            Files.copy(this.file, backup, StandardCopyOption.REPLACE_EXISTING);
            ConfigFile.writeAtomic(this.file, result.merged());
            reload();
        }
    }

    static Map<String, Object> parseMap(String text) {
        return YamlMap.parse(text);
    }
}
