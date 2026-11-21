package org.eu.pcraft.powerfulfireworks.script;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;

/**
 * 一份编译好的脚本：编译产物与一份共享的标准内置对象，源代码只解析一次。
 *
 * <p>入口只有一个，即 {@code function* main(ctx)}。每次 {@link #start} 都新建一个作用域，
 * 顶层 {@code var}/{@code let}/{@code const} 因此各次运行互不影响；标准内置对象（{@code Math}、
 * {@code JSON} 等）通过原型链共享，不重复构造。
 *
 * <p>参数由调用方通过 {@link #start} 或 {@code ctx.run} 传入，脚本从 {@code ctx.params} 读取。
 */
public final class CompiledScript {

    /** 唯一入口名。语言契约，不是配置。 */
    public static final String ENTRY = "main";

    /**
     * 加载期默认指令预算。
     *
     * <p>编译只做解析，几乎不执行指令；这个预算是给"编译期间意外执行了东西"兜底的。
     */
    private static final long DEFAULT_LOAD_BUDGET = 2_000_000L;

    /**
     * 单次启动的指令上限。
     *
     * <p>启动要真正执行一遍脚本顶层（建查表、算常量等），比编译重得多，所以单独给一份额度。
     */
    private static final long START_BUDGET = 200_000L;

    private final String id;
    private final Script script;
    private final Scriptable baseScope;

    private CompiledScript(String id, Script script, Scriptable baseScope) {
        this.id = id;
        this.script = script;
        this.baseScope = baseScope;
    }

    public String id() {
        return id;
    }

    public static CompiledScript compile(String id, String source) {
        if (source == null || source.isBlank()) {
            throw new CompileException(id + ": 脚本内容为空");
        }
        checkEntryShape(id, source);
        return Sandbox.call(new Sandbox.ScriptBudget(DEFAULT_LOAD_BUDGET), cx -> {
            Script compiled;
            try {
                compiled = cx.compileString(source, id, 1, null);
            } catch (RuntimeException e) {
                throw new CompileException(id + ": 编译失败: " + Context.toString(e.getMessage()), e);
            }
            // 标准内置对象约 0.3 ms 一份，按脚本缓存一次；每次运行只新建一个以它为原型的子作用域，
            // 这样顶层变量各归各的，Math/JSON 之类的内置对象又不用重复构造。
            return new CompiledScript(id, compiled, cx.initStandardObjects());
        });
    }

    /**
     * 启动一次执行。
     *
     * @param host   脚本说话的唯一出口
     * @param starter 启动子脚本的入口，已绑定到这次运行
     * @param params 参数对象，原样暴露为 {@code ctx.params}；{@code null} 视为空
     * @param seed   随机种子；同一种子与同一入参应当产生相同结果
     */
    public RunningScript start(ScriptHost host, RunStarter starter, Map<String, Object> params, long seed) {
        Map<String, Object> safeParams = params == null ? new HashMap<>() : new HashMap<>(params);
        host.seed(seed);
        Random random = new Random(seed);

        return Sandbox.call(new Sandbox.ScriptBudget(START_BUDGET), cx -> {
            Scriptable scope = cx.newObject(baseScope);
            scope.setPrototype(baseScope);
            // 父作用域留空：名字解析只沿原型链走 baseScope，脚本够不到 Java 侧的其它作用域。
            scope.setParentScope(null);
            try {
                script.exec(cx, scope);
            } catch (RuntimeException e) {
                throw new CompileException(id + ": 启动失败: " + Context.toString(e.getMessage()), e);
            }

            Object value = ScriptableObject.getProperty(scope, ENTRY);
            if (!(value instanceof Function entry)) {
                throw new CompileException(id + ": 没有入口函数，脚本必须定义 function* main(ctx)");
            }
            ScriptContext ctx = new ScriptContext(cx, scope, host, starter, safeParams, random);
            Object result;
            try {
                // 契约入口只有一个参数；参数通过 ctx.params 读取。
                result = entry.call(cx, scope, scope, new Object[]{ctx});
            } catch (RuntimeException e) {
                throw new CompileException(id + ": 启动失败: " + Context.toString(e.getMessage()), e);
            }
            return RunningScript.create(cx, id, scope, ctx, result);
        });
    }

    /**
     * 在加载期用 AST 确认入口是 generator，不执行脚本。
     *
     * <p>运行期的 {@code Function} 接口没有 generator 标记（{@code BaseFunction#isGeneratorFunction()}
     * 是 protected），所以在解析阶段检查。语法错误留着让 {@code compileString} 报，信息更完整。
     */
    private static void checkEntryShape(String id, String source) {
        AstRoot root;
        try {
            CompilerEnvirons env = new CompilerEnvirons();
            env.setLanguageVersion(Context.VERSION_ES6);
            root = new Parser(env).parse(source, id, 1);
        } catch (RuntimeException e) {
            return;
        }
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof FunctionNode fn && ENTRY.equals(fn.getName())) {
                requireGenerator(id, fn);
                return;
            }
            if (node instanceof VariableDeclaration declaration) {
                for (VariableInitializer init : declaration.getVariables()) {
                    if (init.getTarget() instanceof Name name
                            && ENTRY.equals(name.getIdentifier())
                            && init.getInitializer() instanceof FunctionNode fn) {
                        requireGenerator(id, fn);
                        return;
                    }
                }
            }
        }
    }

    private static void requireGenerator(String id, FunctionNode fn) {
        if (!fn.isGenerator()) {
            throw new CompileException(id + ": 入口 main 必须是 generator 函数（function* main(ctx)）");
        }
    }

    /** 脚本加载或启动失败：语法错误、缺入口、入口不是 generator、顶层执行出错。 */
    public static final class CompileException extends RuntimeException {
        public CompileException(String message) {
            super(message);
        }

        public CompileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
