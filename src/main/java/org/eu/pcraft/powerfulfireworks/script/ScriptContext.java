package org.eu.pcraft.powerfulfireworks.script;

import static org.eu.pcraft.powerfulfireworks.script.ScriptJs.function;
import static org.eu.pcraft.powerfulfireworks.script.ScriptJs.join;

import java.util.Map;
import java.util.Random;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 脚本里的 {@code ctx}：把 Java 侧的能力包成脚本函数。
 *
 * <p>请求解析在 {@link ScriptRequests}，观众在 {@link ScriptAudience}，文字点阵在
 * {@link ScriptText}，参数转换在 {@link ScriptParams}，随机数在 {@link ScriptRandom}；
 * 这里只负责属性接线与 run/spawn 协议。
 */
final class ScriptContext extends ScriptableObject {

    ScriptContext(Context cx, Scriptable scope, ScriptHost host, RunStarter starter,
                  Map<String, ?> params, Random random) {
        setParentScope(scope);
        setPrototype(ScriptableObject.getObjectPrototype(scope));
        putProperty(this, "tick", 0);
        putProperty(this, "params", ScriptParams.toScriptable(cx, scope, params));

        putProperty(this, "log", function(scope, (c, s, args) -> {
            host.log(join(args));
            return Undefined.instance;
        }));
        putProperty(this, "warn", function(scope, (c, s, args) -> {
            host.warn(join(args));
            return Undefined.instance;
        }));

        putProperty(this, "wait", function(scope, (c, s, args) -> {
            double ticks = args.length == 0 ? 1 : Context.toNumber(args[0]);
            if (!Double.isFinite(ticks) || ticks < 1) {
                throw Context.reportRuntimeError("ctx.wait(n) 需要正的 tick 数");
            }
            // 返回对象而不是数字：yield 出来的值要能区分种类（将来还会有发射、演出指令），
            // 用 { wait: n } 这样的标签比裸数字好扩展。
            Scriptable out = c.newObject(s);
            putProperty(out, "wait", ticks);
            return out;
        }));

        putProperty(this, "firework", function(scope, (c, s, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.firework({...}) 需要一个对象参数");
            }
            host.firework(ScriptRequests.firework(spec), args.length > 1 ? ScriptAudience.parse(args[1]) : null);
            return Undefined.instance;
        }));

        putProperty(this, "particle", function(scope, (c, s, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.particle({...}) 需要一个对象参数");
            }
            host.particle(ScriptRequests.particle(spec), args.length > 1 ? ScriptAudience.parse(args[1]) : null);
            return Undefined.instance;
        }));

        putProperty(this, "points", function(scope, (c, s, args) -> {
            if (args.length < 2) {
                throw Context.reportRuntimeError("ctx.points(发射参数, 坐标数组) 需要两个参数");
            }
            if (!(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("第一个参数必须是发射参数对象，例如 { particle: \"dust\" }");
            }
            if (!(args[1] instanceof Scriptable coords)) {
                throw Context.reportRuntimeError("第二个参数必须是坐标数组，例如 [[0, 5, 0], [1, 5, 0]]");
            }
            host.points(ScriptRequests.pointSet(spec, coords),
                    args.length > 2 ? ScriptAudience.parse(args[2]) : null);
            return Undefined.instance;
        }));

        putProperty(this, "text", function(scope, (c, s, args) -> {
            if (args.length == 0 || !(args[0] instanceof Scriptable spec)) {
                throw Context.reportRuntimeError("ctx.text({...}) 需要一个对象参数");
            }
            return ScriptText.shape(host, c, s, spec);
        }));

        putProperty(this, "run", function(scope, (c, s, args) -> {
            RunHandle handle = startChild(starter, args, "ctx.run(id, params) 需要脚本 id");
            Scriptable out = c.newObject(s);
            // 带 join 标签的对象：父运行看到它就挂起，等子运行结束再继续
            putProperty(out, "join", handle);
            return out;
        }));

        putProperty(this, "spawn", function(scope, (c, s, args) -> {
            startChild(starter, args, "ctx.spawn(id, params) 需要脚本 id");
            return Undefined.instance;
        }));

        putProperty(this, "rand", ScriptRandom.create(cx, scope, random));
        putProperty(this, "audience", ScriptAudience.object(cx, scope));
    }

    @Override
    public String getClassName() {
        return "Object";
    }

    void setTick(long tick) {
        putProperty(this, "tick", (double) tick);
    }

    private static String scriptId(Object[] args, String usage) {
        if (args.length == 0) {
            throw Context.reportRuntimeError(usage);
        }
        String id = Context.toString(args[0]).strip();
        if (id.isEmpty()) {
            throw Context.reportRuntimeError(usage);
        }
        return id;
    }

    private static RunHandle startChild(RunStarter starter, Object[] args, String usage) {
        String id = scriptId(args, usage);
        RunHandle handle = starter.start(id, params(args));
        if (handle == null) {
            throw Context.reportRuntimeError("没有脚本 " + id
                    + "，或它不能被当前运行调用（递归、层级过深或已被停掉）");
        }
        return handle;
    }

    private static Map<String, Object> params(Object[] args) {
        if (args.length < 2 || !(args[1] instanceof Scriptable spec)) {
            return Map.of();
        }
        return ScriptParams.toPlain(spec);
    }
}
