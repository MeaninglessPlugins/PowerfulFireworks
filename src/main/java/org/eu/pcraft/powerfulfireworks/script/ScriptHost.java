package org.eu.pcraft.powerfulfireworks.script;

import java.util.Set;

import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;

/**
 * 脚本运行时对外暴露的最小宿主接口。
 *
 * <p>这一层故意做得很窄：脚本只能通过它说话，不能直接拿到插件、Bukkit 或任何 Java 对象。
 * 后续会把"发射 / 演出 / 声音"等有副作用的动作加到这里。
 */
public interface ScriptHost {

    /**
     * 发射一批烟花。
     *
     * <p>宿主负责把请求变成物品与实体包，脚本只描述"要什么"，不关心怎么发。
     * 参数已经在 {@link FireworkRequest} 的构造里校验过，宿主可以直接信任。
     */
    void firework(FireworkRequest request);

    /**
     * 放一批粒子。
     *
     * <p>与烟花平级：同样的原点、同样的接收者，只是走粒子包而不是实体包。
     * 参数已经在 {@link ParticleRequest} 的构造里校验过，宿主可以直接信任。
     */
    void particle(ParticleRequest request);

    /**
     * 在一组坐标上各放一次同一种效果。
     *
     * <p>点集与单次发射共用同一套参数，区别只在于"点从哪来"。
     */
    void points(PointSetRequest request);

    /** 普通日志。 */
    void log(String message);

    /** 警告日志：脚本作者能看懂的问题。 */
    void warn(String message);

    /**
     * 按 id 取字体；没有时返回 {@code null}，由调用方决定怎么报错。
     *
     * <p>字体由插件在装载时解析好，脚本只借用它的度量，不参与解析。
     */
    default BitmapFont font(String id) {
        return null;
    }

    /** 已装载的字体 id，用于把"没有这个字体"报得具体一点。 */
    default Set<String> fonts() {
        return Set.of();
    }

    /**
     * 派生一个子宿主，供子脚本使用。
     *
     * <p>子脚本应当继承父脚本的来源与观众（否则"同一场演出"会被拆到两个地点），
     * 但不该继承父脚本的状态。不需要派生的宿主可以直接返回自己。
     */
    default ScriptHost child() {
        return this;
    }
}
