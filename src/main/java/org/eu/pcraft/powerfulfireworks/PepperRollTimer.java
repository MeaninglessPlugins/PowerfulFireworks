package org.eu.pcraft.powerfulfireworks;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

import java.util.concurrent.ThreadLocalRandom;

public abstract class PepperRollTimer {

    private final Interval<Integer> delayRange;
    protected final PowerfulFireworks plugin;

    @Getter
    private BukkitTask currentTask;

    public PepperRollTimer(Interval<Integer> interval) {
        this.delayRange = interval;
        this.plugin = PowerfulFireworks.getInstance();
    }

    public synchronized void start() {
        // 如果当前已有任务在运行，先停止它，防止重复调度
        stop();

        int delay = calculateDelay();

        currentTask = Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                this::internalRun,
                delay
        );
    }

    public synchronized void stop() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    private void internalRun() {
        try {
            run();
        } finally {
            currentTask = null;
        }
    }

    protected abstract void run();

    private int calculateDelay() {
        int min = delayRange.minimum;
        int max = delayRange.maximum;

        if (min >= max) {
            return min;
        }

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}