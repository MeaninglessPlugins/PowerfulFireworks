package org.eu.pcraft.powerfulfireworks.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * 脚本执行沙箱：限制指令数，越界直接抛错。
 *
 * <p>优化级别固定为 9（生成 JVM 字节码）：编译模式的实际执行明显快于解释模式，
 * 而单 tick 的最坏耗时由这个预算决定，所以不留在解释模式。
 *
 * <p>编译模式下指令计数仍然生效（实测）；失效的是
 * {@link Context#setMaximumInterpreterStackDepth(int)}，因此递归保护改由调用方捕获
 * {@code StackOverflowError}（见 RunningScript#tick）。
 *
 * <p>不做 Java 类访问控制：脚本作者就是服主，那道墙挡不住恶意代码，真正需要的是
 * "脚本只用 ctx 说话"这个约定——由 API 本身而不是沙箱来保证。
 */
public final class Sandbox {

    private static final int OPTIMIZATION_LEVEL = 9;
    private static final int OBSERVE_INTERVAL = 5_000;

    private static final ContextFactory FACTORY = new SandboxContextFactory();
    private static final ThreadLocal<ScriptBudget> CURRENT = new ThreadLocal<>();

    private Sandbox() {
    }

    /** 在沙箱里执行一段同步代码。 */
    public static <T> T call(ScriptBudget budget, Sandboxed<T> body) {
        ScriptBudget previous = CURRENT.get();
        CURRENT.set(budget);
        Context cx = FACTORY.enterContext();
        try {
            cx.setInstructionObserverThreshold(OBSERVE_INTERVAL);
            return body.run(cx);
        } finally {
            try {
                Context.exit();
            } finally {
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
        }
    }

    /** 沙箱内可执行的动作。 */
    @FunctionalInterface
    public interface Sandboxed<T> {
        T run(Context cx);
    }

    /**
     * 脚本越界（指令数、栈深）时抛出的错误。
     *
     * <p>故意继承 {@link Error} 而不是异常：脚本里的 {@code try/catch} 抓不到它，
     * 否则一段 {@code while(true){ try { ... } catch (e) {} }} 就能把预算吞掉继续跑。
     */
    public static final class ScriptBudgetExceededException extends Error {
        public ScriptBudgetExceededException(String message) {
            super(message);
        }
    }

    private static final class SandboxContextFactory extends ContextFactory {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(OPTIMIZATION_LEVEL);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            ScriptBudget budget = CURRENT.get();
            if (budget != null) {
                budget.consume(instructionCount);
            }
        }
    }

    /** 一次运行的指令预算。 */
    public static final class ScriptBudget {
        private final long limit;
        private long used;

        public ScriptBudget(long limit) {
            this.limit = limit;
        }

        public void consume(int instructions) {
            used += instructions;
            if (used > limit) {
                throw new ScriptBudgetExceededException(
                        "script exceeded " + limit + " instructions in one tick");
            }
        }

        /** 每个 tick 开始时重置计数。 */
        public void reset() {
            used = 0;
        }
    }

}
