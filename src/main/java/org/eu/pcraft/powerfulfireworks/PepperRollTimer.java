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
    private boolean running = false; // 状态标记

    public PepperRollTimer(Interval<Integer> interval) {
        this.delayRange = interval;
        this.plugin = PowerfulFireworks.getInstance();
    }

    public void start() {
        running = true;
        scheduleNext();
    }

    private void scheduleNext() {
        stop();
        if (!running) return;
        int delay = calculateDelay();
        currentTask = Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                this::internalRun,
                delay
        );
    }
    public void cancel(){
        running = false;
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }
    public void stop() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    private void internalRun() {
        try {
            run();
        } catch (Exception e) {
            plugin.getLogger().severe("Timer execution failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scheduleNext();
        }
    }

    protected abstract void run();

    private int calculateDelay() {
        int min = delayRange.minimum;
        int max = delayRange.maximum;
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}