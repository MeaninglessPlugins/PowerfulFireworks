package org.eu.pcraft.powerfulfireworks.libs.config;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 注解驱动的配置绑定门面（公共 API）：{@link ConfigModel}/{@link ConfigPath}/
 * {@link ConfigComment}/{@link ConfigRange} 声明模型 → 内部 schema → 类型化装载 / 默认文件
 * 发射（含注释与 kebab 路径）/ 模板守卫。纯 JDK、零 Bukkit。
 *
 * <p>支持 POJO（无参构造 + 字段初始化默认值 + 嵌套 {@code @ConfigModel} 节）与 record
 * （组件路径/注释/范围注解，默认值 = 类型零值，或 {@link ConfigDefaults} 工厂）。两种形态都支持
 * 嵌套 record 节、{@code List&lt;record&gt;} 与 {@code Map&lt;String,record&gt;} 元素（元素类型须标注
 * {@link ConfigModel}，未标注则 schema 构建期报错，不静默回落裸 Map）。</p>
 */
public final class Bindings {

    private Bindings() {}

    // ------------------------------------------------------------------
    // 公共 API
    // ------------------------------------------------------------------

    /** 装载结果：类型化模型 + 值级合法性信号（对齐 ConfigMe {@code PropertyValue.isValidInResource}）。 */
    public record LoadResult<T>(T model, ConfigValues values) {}

    /** 类型化装载：raw map（YamlMap 产物）→ 模型实例；校验问题进 issues。 */
    public static <T> T load(Class<T> model, Map<String, Object> raw, IssueCollector issues) {
        return loadWithValues(model, raw, issues, null).model();
    }

    /**
     * 类型化装载 + 装载后处理钩子：绑定完成后（含类型零值/缺失回落）调用 {@code postLoad}，
     * 供派生字段（基于外部注册表的集合等）解析与追加诊断。
     */
    public static <T> T load(
            Class<T> model, Map<String, Object> raw, IssueCollector issues, ConfigPostLoad<T> postLoad) {
        return loadWithValues(model, raw, issues, postLoad).model();
    }

    /** 类型化装载 + 值级合法性信号（每条目 {@link ConfigValues.Status}：PRESENT/MISSING/INVALID）。 */
    public static <T> LoadResult<T> loadWithValues(Class<T> model, Map<String, Object> raw, IssueCollector issues) {
        return loadWithValues(model, raw, issues, null);
    }

    /** 类型化装载 + 值级合法性信号 + 装载后处理钩子（{@link ConfigPostLoad} 语义同 {@link #load}）。 */
    public static <T> LoadResult<T> loadWithValues(
            Class<T> model, Map<String, Object> raw, IssueCollector issues, ConfigPostLoad<T> postLoad) {
        Model m = modelOf(model);
        SchemaValues sv = m.schema().read(raw, issues);
        ConfigValues values = sv.configValues();
        T bound = bind(model, m, sv, issues);
        if (postLoad != null) {
            postLoad.apply(bound, issues);
        }
        return new LoadResult<>(bound, values);
    }

    /** 默认文件文本：@ConfigHeader 头部注释 + 所有声明条目（含 @ConfigComment 注释、kebab
     *  路径、正确引号）。 */
    public static <T> String defaultsText(Class<T> model) {
        String body = emit(modelOf(model).schema().entries());
        ConfigHeader header = model.getAnnotation(ConfigHeader.class);
        if (header == null || header.value().length == 0) {
            return body;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : header.value()) {
            sb.append(line.isEmpty() ? "#\n" : "# " + line + "\n");
        }
        return sb.append(body).toString();
    }

    /** 模板一致性守卫：默认模板文本 vs 声明 schema——未知键/缺声明键/解析错。 */
    public static <T> List<ConfigIssue> guardTemplate(Class<T> model, String templateText) {
        return SchemaTemplateGuard.check(modelOf(model).schema(), templateText);
    }

    // ------------------------------------------------------------------
    // 模型反射
    // ------------------------------------------------------------------

    /** 单成员抽象：POJO 字段或 record 组件。 */
    private interface Member {
        String path();

        Class<?> type();

        Type genericType();

        List<String> comments();

        ConfigRange range();

        /** 自定义序列化注册点（仅 POJO 字段支持；record 组件为 null）。 */
        default ConfigCodec<?> codec() {
            return null;
        }
    }

    private static final class FieldMember implements Member {
        private final Field field;

        FieldMember(Field field) {
            this.field = field;
        }

        @Override
        public String path() {
            ConfigPath p = this.field.getAnnotation(ConfigPath.class);
            return p != null ? p.value() : kebab(this.field.getName());
        }

        @Override
        public Class<?> type() {
            return this.field.getType();
        }

        @Override
        public Type genericType() {
            return this.field.getGenericType();
        }

        @Override
        public List<String> comments() {
            return commentsOf(this.field);
        }

        @Override
        public ConfigRange range() {
            return this.field.getAnnotation(ConfigRange.class);
        }

        @Override
        public ConfigCodec<?> codec() {
            Codec c = this.field.getAnnotation(Codec.class);
            if (c == null) {
                return null;
            }
            return instantiateCodec(c.value());
        }
    }

    private static final class ComponentMember implements Member {
        private final RecordComponent component;

        ComponentMember(RecordComponent component) {
            this.component = component;
        }

        @Override
        public String path() {
            ConfigPath p = this.component.getAnnotation(ConfigPath.class);
            return p != null ? p.value() : kebab(this.component.getName());
        }

        @Override
        public Class<?> type() {
            return this.component.getType();
        }

        @Override
        public Type genericType() {
            return this.component.getGenericType();
        }

        @Override
        public List<String> comments() {
            return commentsOf(this.component);
        }

        @Override
        public ConfigRange range() {
            return this.component.getAnnotation(ConfigRange.class);
        }

        @Override
        public ConfigCodec<?> codec() {
            Codec c = this.component.getAnnotation(Codec.class);
            if (c == null) {
                return null;
            }
            return instantiateCodec(c.value());
        }
    }

    private record LeafBinding(List<Field> sectionChain, String path, Field field) {}

    /**
     * 嵌套 record 节引用：{@code prefix} 为该节的点号前缀；record 模型按 {@code recordIndex}
     * 回填构造参数，class 模型按 {@code owners}（根到属主的字段链，空 = 根实例）反射写到
     * {@code field}。子节路径已带前缀注册进同一 schema，故构造时复用同一份 {@link SchemaValues}。
     *
     * <p>引用只登记在**节所属的那个模型**自己的 {@code nested} 表里：更深的子节由子模型的
     * {@code modelOf} 在构造时提供（否则第三层节会挂到父模型上，永远不被构造）。</p>
     */
    private record NestedRef(String prefix, Class<?> type, int recordIndex, List<Field> owners, Field field) {}

    private record Model(ConfigSchema schema, List<LeafBinding> leaves, List<NestedRef> nested, boolean isRecord) {}

    /** 声明缓存：schema/叶子/嵌套引用只依赖类结构，与磁盘无关。 */
    private static final ClassValue<Model> MODELS = new ClassValue<>() {
        @Override
        protected Model computeValue(Class<?> type) {
            return declare(type);
        }
    };

    private static Model modelOf(Class<?> model) {
        return MODELS.get(model);
    }

    private static Model declare(Class<?> model) {
        ConfigSchema.Builder b = ConfigSchema.builder();
        Object defaults = defaultInstanceOf(model);
        List<NestedRef> nested = new ArrayList<>();
        if (model.isRecord()) {
            RecordComponent[] rcs = model.getRecordComponents();
            for (int i = 0; i < rcs.length; i++) {
                Member m = new ComponentMember(rcs[i]);
                if (isSection(m.type())) {
                    nested.add(new NestedRef(m.path(), m.type(), i, List.of(), null));
                    flattenInto(b, m.path(), m.type(), sectionDefault(defaults, m, m.type()));
                } else {
                    checkCollectionElements(m, m.type());
                    addEntry(b, m.path(), m, defaultOf(defaults, m, m.type()));
                }
            }
            return new Model(b.build(), List.of(), List.copyOf(nested), true);
        }
        List<LeafBinding> leaves = new ArrayList<>();
        Object inst = instantiate(model, true);
        walkFields(model, inst, "", List.of(), b, leaves, nested);
        return new Model(b.build(), List.copyOf(leaves), List.copyOf(nested), false);
    }

    /** record 节（不可变值类型，整体构造）：{@code @ConfigModel} 标注的 record 类型。 */
    private static boolean isSection(Class<?> type) {
        if (!type.isRecord()) {
            if (type.isAnnotationPresent(ConfigModel.class)) {
                throw new IllegalArgumentException(
                        "record 节只接受 record 类型（可变 class 节请用 POJO 模型字段声明）: " + type.getName());
            }
            return false;
        }
        return type.isAnnotationPresent(ConfigModel.class);
    }

    /** 节的默认实例：外层默认实例的该分量优先，否则节类型自己的 {@code @ConfigDefaults}。 */
    private static Object sectionDefault(Object defaults, Member m, Class<?> type) {
        Object sub = defaults == null ? null : readMember(defaults, m);
        return sub != null ? sub : defaultInstanceOf(type);
    }

    /**
     * 把 record 节按前缀平铺进同一 schema：只做"声明"，不登记节引用——该节自己的引用由
     * {@code modelOf(节类型).nested()} 在构造时提供。
     */
    private static void flattenInto(ConfigSchema.Builder b, String prefix, Class<?> type, Object defaults) {
        for (RecordComponent rc : type.getRecordComponents()) {
            Member m = new ComponentMember(rc);
            String path = join(prefix, m.path());
            if (isSection(m.type())) {
                flattenInto(b, path, m.type(), sectionDefault(defaults, m, m.type()));
            } else {
                checkCollectionElements(m, m.type());
                addEntry(b, path, m, defaultOf(defaults, m, m.type()));
            }
        }
    }

    private static void walkFields(
            Class<?> clazz,
            Object instance,
            String prefix,
            List<Field> sectionChain,
            ConfigSchema.Builder b,
            List<LeafBinding> leaves,
            List<NestedRef> nested) {
        for (Field f : clazz.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            FieldMember m = new FieldMember(f);
            String fullPath = prefix.isEmpty() ? m.path() : prefix + "." + m.path();
            if (f.getType().isAnnotationPresent(ConfigModel.class)
                    && !f.getType().isRecord()) {
                Object sub = read(f, instance);
                if (sub == null) {
                    sub = instantiate(f.getType(), true);
                }
                List<Field> chain = new ArrayList<>(sectionChain);
                chain.add(f);
                walkFields(f.getType(), sub, fullPath, chain, b, leaves, nested);
                continue;
            }
            if (f.getType().isAnnotationPresent(ConfigModel.class)) {
                // 嵌套 record 节：整体构造后写回该字段（record 字段不可反射改写）。
                Object sub = read(f, instance);
                if (sub == null) {
                    sub = defaultInstanceOf(f.getType());
                }
                nested.add(new NestedRef(fullPath, f.getType(), -1, List.copyOf(sectionChain), f));
                flattenInto(b, fullPath, f.getType(), sub);
                continue;
            }
            Object def = read(f, instance);
            if (def == null) {
                def = typeZero(f.getType());
            }
            checkCollectionElements(m, f.getType());
            addEntry(b, fullPath, m, def);
            leaves.add(new LeafBinding(sectionChain, fullPath, f));
        }
    }

    /** 成员的默认值：默认实例上的分量优先，否则类型零值。 */
    private static Object defaultOf(Object defaults, Member m, Class<?> type) {
        if (defaults == null) {
            return typeZero(type);
        }
        Object value = readMember(defaults, m);
        return value == null ? typeZero(type) : value;
    }

    /** 从默认实例读分量（record 组件或字段）。 */
    private static Object readMember(Object instance, Member m) {
        return m instanceof ComponentMember cm ? read(cm.component, instance) : read(((FieldMember) m).field, instance);
    }

    /** 集合元素类型检查：record 元素必须标注 {@link ConfigModel}，否则 schema 构建期报错。 */
    private static void checkCollectionElements(Member m, Class<?> type) {
        Type generic = m.genericType();
        if (List.class.isAssignableFrom(type)) {
            checkElementType(m.path(), elementType(generic));
            return;
        }
        if (Map.class.isAssignableFrom(type)) {
            Type[] args = mapTypeArgs(generic);
            if (args != null && args[1] instanceof Class<?> valueType) {
                checkElementType(m.path(), valueType);
            }
        }
    }

    private static void checkElementType(String path, Class<?> element) {
        if (element != null && element.isRecord() && !element.isAnnotationPresent(ConfigModel.class)) {
            throw new IllegalArgumentException("record 元素类型须标注 @ConfigModel（键 " + path + "）: " + element.getName());
        }
    }

    private static String join(String prefix, String path) {
        return prefix == null || prefix.isEmpty() ? path : prefix + "." + path;
    }

    /** {@link ConfigDefaults} 工厂给出的默认实例；未标注返回 null。 */
    private static Object defaultInstanceOf(Class<?> model) {
        Method factory = null;
        for (Method candidate : model.getDeclaredMethods()) {
            if (candidate.isAnnotationPresent(ConfigDefaults.class)) {
                if (factory != null) {
                    throw new IllegalArgumentException("@ConfigDefaults 只能标注一个方法: " + model.getName());
                }
                factory = candidate;
            }
        }
        if (factory == null) {
            return null;
        }
        if (!Modifier.isStatic(factory.getModifiers())
                || factory.getParameterCount() != 0
                || !model.isAssignableFrom(factory.getReturnType())) {
            throw new IllegalArgumentException(
                    "@ConfigDefaults 方法须为 static 无参且返回模型类型: " + model.getName() + "#" + factory.getName());
        }
        factory.setAccessible(true);
        try {
            return factory.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot invoke @ConfigDefaults factory " + factory, e);
        }
    }

    private static void addEntry(ConfigSchema.Builder b, String key, Member m, Object def) {
        ConfigCodec<?> codec = m.codec();
        if (codec != null) {
            b.codecField(key, def, codec, m.comments());
            return;
        }
        ConfigRange range = m.range();
        double min = range == null ? Double.NaN : range.min();
        double max = range == null ? Double.NaN : range.max();
        boolean clamp = range != null && range.clamp();
        b.field(
                key,
                typeOf(m.type()),
                def,
                enumClassOf(m.type()),
                temporalClassOf(m.type()),
                boundsOf(range),
                min,
                max,
                clamp,
                m.comments());
    }

    // ------------------------------------------------------------------
    // 装载
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> T bind(Class<T> model, Model m, SchemaValues sv, IssueCollector issues) {
        if (m.isRecord()) {
            return buildRecord(model, sv, "", issues);
        }
        Object inst = instantiate(model, true);
        for (LeafBinding lb : m.leaves()) {
            Object owner = inst;
            for (Field sec : lb.sectionChain()) {
                owner = read(sec, owner);
            }
            set(
                    lb.field(),
                    owner,
                    coerce(sv.raw(lb.path()), lb.field().getType(), lb.field().getGenericType(), issues));
        }
        for (NestedRef ref : m.nested()) {
            Object owner = inst;
            for (Field section : ref.owners()) {
                owner = read(section, owner);
            }
            set(ref.field(), owner, buildRecord(ref.type(), sv, ref.prefix(), issues));
        }
        return (T) inst;
    }

    /**
     * 构造 record 实例：嵌套 record 节按前缀整体构造（子节路径已带前缀进同一 schema），
     * 非节成员按泛型强制（含 record 集合元素）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T buildRecord(Class<T> model, SchemaValues sv, String prefix, IssueCollector issues) {
        RecordComponent[] rcs = model.getRecordComponents();
        Map<Integer, NestedRef> sections = new java.util.HashMap<>();
        for (NestedRef ref : modelOf(model).nested()) {
            if (ref.recordIndex() >= 0) {
                sections.put(ref.recordIndex(), ref);
            }
        }
        Class<?>[] types = new Class<?>[rcs.length];
        Object[] args = new Object[rcs.length];
        for (int i = 0; i < rcs.length; i++) {
            types[i] = rcs[i].getType();
            NestedRef section = sections.get(i);
            if (section != null) {
                // 节引用里的 prefix 是相对本模型的路径，须叠加调用方前缀（三层以上嵌套的关键）。
                args[i] = buildRecord(section.type(), sv, join(prefix, section.prefix()), issues);
                continue;
            }
            Member m = new ComponentMember(rcs[i]);
            args[i] = coerce(sv.raw(join(prefix, m.path())), types[i], rcs[i].getGenericType(), issues);
        }
        try {
            Constructor<?> ctor = model.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return (T) ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot construct record " + model.getName(), e);
        }
    }

    /** 值 → 字段/组件类型强制（null → 类型零值；List 元素按泛型元素类型递归；Map 键值按泛型宽容）。 */
    private static Object coerce(Object value, Class<?> target, Type generic, IssueCollector issues) {
        if (value == null) {
            return typeZero(target);
        }
        if (target == boolean.class || target == Boolean.class) {
            return value instanceof Boolean b ? b : typeZero(target);
        }
        if (target == int.class || target == Integer.class) {
            return value instanceof Number n ? n.intValue() : typeZero(target);
        }
        if (target == long.class || target == Long.class) {
            return value instanceof Number n ? n.longValue() : typeZero(target);
        }
        if (target == double.class || target == Double.class) {
            return value instanceof Number n ? n.doubleValue() : typeZero(target);
        }
        if (target == String.class) {
            return String.valueOf(value);
        }
        if (target.isEnum()) {
            if (value instanceof Enum<?>) {
                return value;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object e = Enum.valueOf((Class) target, String.valueOf(value).trim().toUpperCase(Locale.ROOT));
            return e;
        }
        if (List.class.isAssignableFrom(target)) {
            if (!(value instanceof List<?> l)) {
                return List.of();
            }
            Class<?> elem = elementType(generic);
            if (elem == null) {
                return l;
            }
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) {
                out.add(coerce(o, elem, null, issues));
            }
            return out;
        }
        if (Map.class.isAssignableFrom(target)) {
            if (!(value instanceof Map<?, ?> m)) {
                return typeZero(target);
            }
            Type[] args = mapTypeArgs(generic);
            Class<?> keyType = args != null && args[0] instanceof Class<?> k ? k : null;
            Class<?> valType = args != null && args[1] instanceof Class<?> v ? v : null;
            Map<Object, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(coerceKey(e.getKey(), keyType), coerceValue(e.getValue(), valType, issues));
            }
            return out;
        }
        if (target == Optional.class) {
            // Optional<T>：拆包后按泛型元素强制；缺失/空 → Optional.empty()（对齐 ConfigMe OptionalProperty）。
            Class<?> elem = elementType(generic);
            if (value instanceof Optional<?> o) {
                if (!o.isPresent() || elem == null) {
                    return o;
                }
                return Optional.ofNullable(coerce(o.get(), elem, null, issues));
            }
            if (value == null) {
                return Optional.empty();
            }
            return elem == null ? Optional.ofNullable(value) : Optional.ofNullable(coerce(value, elem, null, issues));
        }
        if (Set.class.isAssignableFrom(target)) {
            // Set<T>：LinkedHashSet 保序去重，元素按泛型强制（对齐 ConfigMe SetProperty）。
            if (!(value instanceof java.util.Collection<?> c)) {
                return typeZero(target);
            }
            Class<?> elem = elementType(generic);
            java.util.LinkedHashSet<Object> out = new java.util.LinkedHashSet<>();
            for (Object o : c) {
                out.add(elem == null ? o : coerce(o, elem, null, issues));
            }
            return out;
        }
        if (target.isArray()) {
            // T[] / 基本类型数组：集合/数组 → 目标数组（元素逐个强制）。
            List<?> items = asList(value);
            if (items == null) {
                return typeZero(target);
            }
            Class<?> comp = target.getComponentType();
            Object arr = java.lang.reflect.Array.newInstance(comp, items.size());
            for (int i = 0; i < items.size(); i++) {
                java.lang.reflect.Array.set(arr, i, coerceElement(comp, items.get(i), issues));
            }
            return arr;
        }
        if (target == LocalDate.class || target == LocalTime.class || target == LocalDateTime.class) {
            // 时间类型：schema 已解析（LocalDate 等）或字符串/Date 宽容解析（对齐 ConfigMe TemporalType）。
            if (target.isInstance(value)) {
                return value;
            }
            Object parsed = TemporalValues.parse(value, target);
            return parsed != null ? parsed : typeZero(target);
        }
        if (target.isRecord() && target.isAnnotationPresent(ConfigModel.class)) {
            // record 集合元素：按元素自身 schema 构造（缺键回落该 record 的 ConfigDefaults/类型零值）。
            return bindElementModel(target, value instanceof Map<?, ?> mm ? mm : Map.of(), issues);
        }
        return value;
    }

    /** 集合元素 record：raw map → 元素模型实例（元素级诊断并入同一 IssueCollector）。 */
    @SuppressWarnings("unchecked")
    private static Object bindElementModel(Class<?> model, Map<?, ?> raw, IssueCollector issues) {
        Map<String, Object> stringKeyed = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            stringKeyed.put(String.valueOf(e.getKey()), e.getValue());
        }
        return loadWithValues((Class<Object>) model, stringKeyed, issues).model();
    }

    /** 集合/数组 → List 视图；非集合非数组返回 null。 */
    private static List<?> asList(Object value) {
        if (value instanceof List<?> l) {
            return l;
        }
        if (value instanceof java.util.Collection<?> c) {
            return new ArrayList<>(c);
        }
        if (value != null && value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(java.lang.reflect.Array.get(value, i));
            }
            return out;
        }
        return null;
    }

    /** 数组元素强制：基本类型/字符串/枚举按目标收窄，其余递归 {@link #coerce}。 */
    private static Object coerceElement(Class<?> comp, Object o, IssueCollector issues) {
        if (comp == int.class || comp == Integer.class) {
            return o instanceof Number n ? n.intValue() : 0;
        }
        if (comp == long.class || comp == Long.class) {
            return o instanceof Number n ? n.longValue() : 0L;
        }
        if (comp == double.class || comp == Double.class) {
            return o instanceof Number n ? n.doubleValue() : 0.0;
        }
        if (comp == boolean.class || comp == Boolean.class) {
            return o instanceof Boolean b ? b : false;
        }
        if (comp == String.class) {
            return String.valueOf(o);
        }
        if (comp.isEnum()) {
            if (o instanceof Enum<?>) {
                return o;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object e = Enum.valueOf((Class) comp, String.valueOf(o).trim().toUpperCase(Locale.ROOT));
            return e;
        }
        return coerce(o, comp, null, issues);
    }

    /** Map 键强制：Integer/Long 键接受 Number 或可解析 String；其它键按 String.valueOf。 */
    private static Object coerceKey(Object key, Class<?> keyType) {
        if (keyType == Integer.class || keyType == int.class) {
            if (key instanceof Number n) {
                return n.intValue();
            }
            if (key instanceof String s) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {
                    return String.valueOf(key);
                }
            }
        }
        if (keyType == Long.class || keyType == long.class) {
            if (key instanceof Number n) {
                return n.longValue();
            }
            if (key instanceof String s) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    return String.valueOf(key);
                }
            }
        }
        return String.valueOf(key);
    }

    /** Map 值强制：类型零值/枚举/List 递归；非目标类型回落零值。 */
    private static Object coerceValue(Object value, Class<?> valueType, IssueCollector issues) {
        if (valueType == null || valueType == Object.class) {
            return value;
        }
        return coerce(value, valueType, null, issues);
    }

    private static Type[] mapTypeArgs(Type generic) {
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 2) {
            return pt.getActualTypeArguments();
        }
        return null;
    }

    private static Class<?> elementType(Type generic) {
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 默认文件发射
    // ------------------------------------------------------------------

    private static String emit(List<ConfigSchema.Entry> entries) {
        StringBuilder sb = new StringBuilder();
        // 已开节链（根 → 最深，元素 = 各层节键）。
        List<String> chain = new ArrayList<>();
        for (ConfigSchema.Entry e : entries) {
            List<String> segs = ConfigPaths.split(e.key());
            int leafDepth = segs.size();
            // 最长公共前缀：兄弟节（同深度异键）须先关掉旧链再开新节。
            int common = 0;
            int maxCommon = Math.min(chain.size(), leafDepth - 1);
            while (common < maxCommon && chain.get(common).equals(segs.get(common))) {
                common++;
            }
            while (chain.size() > common) {
                chain.remove(chain.size() - 1);
            }
            for (int i = chain.size(); i < leafDepth - 1; i++) {
                sb.append(indent(i)).append(segs.get(i)).append(":\n");
                chain.add(segs.get(i));
            }
            for (String c : e.comments()) {
                sb.append(indent(leafDepth - 1))
                        .append(c.isEmpty() ? "#" : "# " + c)
                        .append('\n');
            }
            Object emitValue;
            if (e.type() == ConfigSchema.ValueType.CODEC) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object encoded = ((ConfigCodec<Object>) e.codec()).toConfig(e.def());
                emitValue = encoded;
            } else {
                emitValue = e.def();
            }
            if (emitValue instanceof Optional<?> o) {
                // Optional 条目：空 → 空标量（回读为 null → Optional.empty()）；有值 → 内值发射。
                emitValue = o.orElse(null);
            }
            if (emitValue instanceof Map<?, ?> mapValue) {
                // Map 一等条目：叶子键作节，条目逐行缩进（空 Map → {}）。
                sb.append(indent(leafDepth - 1))
                        .append(segs.get(leafDepth - 1))
                        .append(mapValue.isEmpty() ? ": {}\n" : ":\n");
                for (Map.Entry<?, ?> me : mapValue.entrySet()) {
                    sb.append(indent(leafDepth))
                            .append(YamlScalar.encode(me.getKey()))
                            .append(": ")
                            .append(YamlScalar.encode(me.getValue()))
                            .append('\n');
                }
                continue;
            }
            if (emitValue == null) {
                sb.append(indent(leafDepth - 1)).append(segs.get(leafDepth - 1)).append(":\n");
                continue;
            }
            sb.append(indent(leafDepth - 1))
                    .append(segs.get(leafDepth - 1))
                    .append(": ")
                    .append(YamlScalar.encode(emitValue))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String indent(int depth) {
        return "  ".repeat(depth);
    }

    // ------------------------------------------------------------------
    // 类型工具
    // ------------------------------------------------------------------

    private static ConfigSchema.ValueType typeOf(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return ConfigSchema.ValueType.BOOL;
        }
        if (type == int.class || type == Integer.class) {
            return ConfigSchema.ValueType.INT;
        }
        if (type == long.class || type == Long.class) {
            return ConfigSchema.ValueType.LONG;
        }
        if (type == double.class || type == Double.class) {
            return ConfigSchema.ValueType.DOUBLE;
        }
        if (type == String.class) {
            return ConfigSchema.ValueType.STRING;
        }
        if (type.isEnum()) {
            return ConfigSchema.ValueType.ENUM;
        }
        if (type == Optional.class) {
            return ConfigSchema.ValueType.OPTIONAL;
        }
        if (Set.class.isAssignableFrom(type)) {
            return ConfigSchema.ValueType.SET;
        }
        if (type.isArray()) {
            return ConfigSchema.ValueType.ARRAY;
        }
        if (type == LocalDate.class || type == LocalTime.class || type == LocalDateTime.class) {
            return ConfigSchema.ValueType.TEMPORAL;
        }
        if (List.class.isAssignableFrom(type)) {
            return ConfigSchema.ValueType.LIST;
        }
        if (Map.class.isAssignableFrom(type)) {
            return ConfigSchema.ValueType.MAP;
        }
        throw new IllegalArgumentException("unsupported config field type: " + type.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<? extends Enum<?>> enumClassOf(Class<?> type) {
        return type.isEnum() ? (Class<? extends Enum<?>>) type : null;
    }

    /** TEMPORAL 条目的目标时间类；其余类型返回 null。 */
    private static Class<?> temporalClassOf(Class<?> type) {
        if (type == LocalDate.class || type == LocalTime.class || type == LocalDateTime.class) {
            return type;
        }
        return null;
    }

    private static Predicate<Number> boundsOf(ConfigRange range) {
        if (range == null || (Double.isNaN(range.min()) && Double.isNaN(range.max()))) {
            return null;
        }
        return n -> (Double.isNaN(range.min()) || n.doubleValue() >= range.min())
                && (Double.isNaN(range.max()) || n.doubleValue() <= range.max());
    }

    private static List<String> commentsOf(AnnotatedElement e) {
        ConfigComment[] arr = e.getAnnotationsByType(ConfigComment.class);
        List<String> out = new ArrayList<>();
        for (ConfigComment c : arr) {
            out.addAll(Arrays.asList(c.value()));
        }
        return out;
    }

    private static Object typeZero(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        if (type == double.class || type == Double.class) {
            return 0.0;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == String.class) {
            return "";
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (Set.class.isAssignableFrom(type)) {
            return Set.of();
        }
        if (type.isArray()) {
            return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
        }
        if (type == LocalDate.class || type == LocalTime.class || type == LocalDateTime.class) {
            return null;
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        if (Map.class.isAssignableFrom(type)) {
            return Map.of();
        }
        if (type.isEnum()) {
            Object[] cs = type.getEnumConstants();
            return cs.length > 0 ? cs[0] : null;
        }
        return null;
    }

    private static Object instantiate(Class<?> c, boolean required) {
        try {
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            if (required) {
                throw new IllegalArgumentException("config model requires a no-arg constructor: " + c.getName());
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot instantiate config model " + c.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConfigCodec<?> instantiateCodec(Class<? extends ConfigCodec<?>> codecClass) {
        try {
            Constructor<?> ctor = codecClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (ConfigCodec<?>) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("codec 需要无参构造: " + codecClass.getName(), e);
        }
    }

    private static Object read(Field f, Object instance) {
        try {
            return f.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 读 record 组件值（经其 accessor；record 组件字段不可反射读改写）。 */
    private static Object read(RecordComponent rc, Object instance) {
        try {
            return rc.getAccessor().invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field f, Object instance, Object value) {
        try {
            f.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
