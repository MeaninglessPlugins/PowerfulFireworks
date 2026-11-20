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

    private final String id;
    private final Scriptable scope;
    private final Scriptable ctx;
    private final Scriptable generator;
    private final Function resume;
    private final Sandbox.ScriptBudget budget = new Sandbox.ScriptBudget(TICK_BUDGET);

    private State state = State.RUNNING;
    private long tick = -1L;
    private long resumeAt = 0L;
    private Throwable failure;
    private RunHandle joining;

    private RunningScript(String id, Scriptable scope, Scriptable ctx, Scriptable generator,
                          Function resume) {
        this.id = id;
        this.scope = scope;
        this.ctx = ctx;
        this.generator = generator;
        this.resume = resume;
    }

    static RunningScript create(Context cx, String id, Scriptable scope, Scriptable ctx,
                                Object entryResult) {
        if (entryResult instanceof Scriptable obj
                && ScriptableObject.getProperty(obj, "next") instanceof Function) {
            // 从 Java 直接调 g.next() 会被 Rhino 拒绝（恢复生成器必须经过脚本激活帧），
            // 所以这里编译一个单行 JS 函数作为转接。
            Function helper = (Function) cx.evaluateString(scope,
                    "(function(g, v){ return g.next(v); })", "resume", 1, null);
            return new RunningScript(id, scope, ctx, obj, helper);
        }
        // 编译期已经保证入口是 generator，这里只是防御：非生成器视为当场结束。
        RunningScript done = new RunningScript(id, scope, ctx, null, null);
        done.state = State.COMPLETED;
        return done;
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
                    ScriptContextObject.setTick(ctx, now);
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
                if (value instanceof Scriptable awaited && ScriptableObject.hasProperty(awaited, "wait")) {
                    double ticks = Context.toNumber(ScriptableObject.getProperty(awaited, "wait"));
                    long delay = Double.isNaN(ticks) || ticks < 0 ? 1 : (long) Math.ceil(ticks);
                    resumeAt = now + Math.max(delay, 0);
                    if (resumeAt > now) {
                        return;
                    }
                    continue;
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
            }
            // yield 出来的是不认识的指令：下一 tick 再试，避免同 tick 空转。
            resumeAt = now + 1;
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
