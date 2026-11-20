package org.eu.pcraft.powerfulfireworks.script;

/**
 * 一次脚本运行的句柄：状态、进度、取消。
 *
 * <p>状态只转移一次：先到者生效，之后查询返回同一个终态；取消不覆盖已完成的 Run，
 * 也不触发完成回调。
 */
public interface RunHandle {

    /** 运行状态。 */
    enum State {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED;

        public boolean finished() {
            return this != RUNNING;
        }
    }

    /**
     * 运行进度快照。
     *
     * <p>只放数据，不放展示文案：状态名称、时间描述、错误提示都由调用方按自己的语言与格式组装。
     *
     * @param tick     最近一次推进到的服务端 tick；尚未推进时为 -1
     * @param resumeAt 下一次应当被推进的 tick；不等待时为 {@code tick}
     * @param failure  失败原因；未失败时为 {@code null}
     */
    record Progress(long tick, long resumeAt, Throwable failure) {
    }

    String id();

    State state();

    /** 是否已经进入终态（完成、失败或取消）。 */
    default boolean finished() {
        return state().finished();
    }

    Progress progress();

    /** 对已经结束的运行无效果。 */
    void cancel();
}
