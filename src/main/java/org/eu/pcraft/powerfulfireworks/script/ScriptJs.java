package org.eu.pcraft.powerfulfireworks.script;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * ctx 上那些函数的胶水：把 Java lambda 包成脚本能调用的函数对象。
 *
 * <p>单独放一个文件，是因为 ctx 与 audience 的构造都要用它，
 * 放在任意一边都会让另一边反向依赖。
 */
final class ScriptJs {

    private ScriptJs() {
    }

    static BaseFunction function(Scriptable scope, Fn body) {
        BaseFunction fn = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                return body.apply(cx, s, args);
            }

            @Override
            public String getFunctionName() {
                // 不把 Java 合成类名暴露给脚本的堆栈与日志
                return "";
            }
        };
        fn.setParentScope(scope);
        fn.setPrototype(ScriptableObject.getFunctionPrototype(scope));
        return fn;
    }

    /** 把 log/warn 的参数拼成一行，保持脚本里写多个值时的可读性。 */
    static String join(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Context.toString(args[i]));
        }
        return sb.toString();
    }

    @FunctionalInterface
    interface Fn {
        Object apply(Context cx, Scriptable scope, Object[] args);
    }
}
