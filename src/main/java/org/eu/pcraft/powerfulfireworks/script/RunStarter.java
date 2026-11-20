package org.eu.pcraft.powerfulfireworks.script;

import java.util.Map;


/**
 * 从脚本内部启动另一个脚本的入口。
 *
 * <p>由运行时注入，已经绑好"父运行是哪一次"；脚本只给脚本 id 与参数，
 * 命名、去重、递归与深度检查都由运行时负责，脚本层不重复实现。
 */
@FunctionalInterface
public interface RunStarter {

    /**
     * 启动一个子脚本。
     *
     * @return 子运行的句柄；没有这个脚本、或因为递归/深度被拒绝时返回 {@code null}
     */
    RunHandle start(String scriptId, Map<String, Object> params);
}
