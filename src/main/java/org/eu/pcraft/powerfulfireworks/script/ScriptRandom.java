package org.eu.pcraft.powerfulfireworks.script;

import static org.eu.pcraft.powerfulfireworks.script.ScriptJs.function;
import static org.mozilla.javascript.ScriptableObject.putProperty;

import java.util.Random;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * 构造 {@code ctx.rand}：脚本可用的随机数函数，绑定本次运行的 {@link Random}。
 */
final class ScriptRandom {

    private ScriptRandom() {
    }

    static Scriptable create(Context cx, Scriptable scope, Random random) {
        Scriptable rand = cx.newObject(scope);
        putProperty(rand, "int", function(scope, (c, s, args) -> {
            if (args.length != 2) {
                throw Context.reportRuntimeError("ctx.rand.int(min, max) 需要两个参数");
            }
            int min = (int) Context.toNumber(args[0]);
            int max = (int) Context.toNumber(args[1]);
            if (max < min) {
                throw Context.reportRuntimeError("ctx.rand.int: max 不能小于 min");
            }
            return min + random.nextInt(max - min + 1);
        }));
        putProperty(rand, "float", function(scope, (c, s, args) -> {
            double min = args.length > 0 ? Context.toNumber(args[0]) : 0.0;
            double max = args.length > 1 ? Context.toNumber(args[1]) : 1.0;
            return min + random.nextDouble() * (max - min);
        }));
        putProperty(rand, "pick", function(scope, (c, s, args) -> {
            if (args.length == 0) {
                throw Context.reportRuntimeError("ctx.rand.pick(...) 至少需要一个候选值");
            }
            int idx = random.nextInt(args.length);
            return args[idx];
        }));
        putProperty(rand, "chance", function(scope, (c, s, args) -> {
            double p = args.length == 0 ? 0.5 : Context.toNumber(args[0]);
            return random.nextDouble() < p;
        }));
        return rand;
    }
}
