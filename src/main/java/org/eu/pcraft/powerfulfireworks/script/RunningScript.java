package org.eu.pcraft.powerfulfireworks.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 一次脚本执行，同时是这次运行对外的 {@link RunHandle}。
 *
 * <p>只保存状态与数字，不保存给人看的文案——同一个 Run 在日志、命令、状态查询里的措辞由查询方决定。
 */
public final class RunningScript implements RunHandle {

    private static final int MAX_STEPS_PER_TICK = 64;
    private static final long TICK_BUDGET = 1_000_000L;
    private static final long MAX_WAIT = 24_000L;

    private final String id;
    private final Scriptable scope;
    private final ScriptContext ctx;
    private final Scriptable generator;
    private final Function resume;
    private final Sandbox.ScriptBudget budget = new Sandbox.ScriptBudget(TICK_BUDGET);

    private State state = State.RUNNING;
    private long tick = -1L;
    private long resumeAt = 0L;
    private Throwable failure;
    private RunHandle joining;

    private RunningScript(String id, Scriptable scope, ScriptContext ctx, Scriptable generator,
                          Function resume) {
        this.id = id;
        this.scope = scope;
        this.ctx = ctx;
        this.generator = generator;
        this.resume = resume;
    }

    static RunningScript create(Context cx, String id, Scriptable scope, ScriptContext ctx,
                                Object entryResult) {
        if (!(entryResult instanceof Scriptable obj)
                || !(ScriptableObject.getProperty(obj, "next") instanceof Function)) {
            throw new CompiledScript.CompileException(id + ": 入口 main 必须是 generator 函数（function* main(ctx)）");
        }
        // 从 Java 直接调 g.next() 会被 Rhino 拒绝（恢复生成器必须经过脚本激活帧），
        // 所以这里编译一个单行 JS 函数作为转接。
        Function helper = (Function) cx.evaluateString(scope,
                "(function(g, v){ return g.next(v); })", "resume", 1, null);
        return new RunningScript(id, scope, ctx, obj, helper);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public Progress progress() {
        return new Progress(tick, resumeAt, failure);
    }

    /** 推进到 {@code now} 这个服务端 tick。可以重复调用，内部按 {@code resumeAt} 自行判断。 */
    public void tick(long now) {
        if (state != State.RUNNING) {
            return;
        }
        tick = now;
        if (joining != null) {
            if (!joining.finished()) {
                return;   // 子运行还在跑，父运行继续等；每 tick 只看一次状态
            }
            joining = null;
        }
        int steps = 0;
        while (state == State.RUNNING && now >= resumeAt && steps < MAX_STEPS_PER_TICK) {
            steps++;
            budget.reset();
            Object outcome;
            try {
                outcome = Sandbox.call(budget, cx -> {
                    ctx.setTick(now);
                    return resume.call(cx, scope, scope, new Object[]{generator, Undefined.instance});
                });
            } catch (Sandbox.ScriptBudgetExceededException e) {
                fail(e);
                return;
            } catch (RuntimeException | StackOverflowError e) {
                // StackOverflowError 不是 Exception：脚本递归过深只会掉到这里，同样按失败处理。
                fail(e);
                return;
            }
            if (outcome instanceof Scriptable out) {
                if (Context.toBoolean(ScriptableObject.getProperty(out, "done"))) {
                    state = State.COMPLETED;
                    return;
                }
                Object value = ScriptableObject.getProperty(out, "value");
                if (value == Undefined.instance) {
                    resumeAt = now + 1;
                    return;
                }
                if (value instanceof Scriptable awaited && ScriptableObject.hasProperty(awaited, "wait")) {
                    double ticks = Context.toNumber(ScriptableObject.getProperty(awaited, "wait"));
                    if (!Double.isFinite(ticks) || ticks < 1) {
                        fail(new IllegalStateException("ctx.wait(n) 需要正的 tick 数"));
                        return;
                    }
                    resumeAt = now + Math.min((long) Math.ceil(ticks), MAX_WAIT);
                    return;
                }
                if (value instanceof Scriptable awaited && ScriptableObject.hasProperty(awaited, "join")) {
                    Object child = ScriptableObject.getProperty(awaited, "join");
                    if (child instanceof RunHandle handle) {
                        if (handle.finished()) {
                            continue;   // 子运行已经结束，不必挂起
                        }
                        joining = handle;
                        return;
                    }
                }
                fail(new IllegalStateException("未知的 yield 值：" + Context.toString(value)
                        + "；等待请用 ctx.wait(n)，等待子脚本请 yield ctx.run(...)"));
                return;
            }
            fail(new IllegalStateException("生成器返回了非对象结果：" + Context.toString(outcome)));
            return;
        }
        if (steps >= MAX_STEPS_PER_TICK) {
            resumeAt = now + 1;
        }
    }

    @Override
    public void cancel() {
        if (state == State.RUNNING) {
            state = State.CANCELLED;
            resumeAt = tick;   // 终态不该再报告"等待到某个 tick"
        }
    }

    private void fail(Throwable cause) {
        state = State.FAILED;
        failure = cause;
        resumeAt = tick;   // 同上：终态不该再报告等待时间
    }
}
