package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

import java.util.Random;

public class PepperRollTimer {
    Interval<Integer> delay;
    PowerfulFireworks plugin;
    BukkitTask task;
    Random rand=new Random();
    public PepperRollTimer(Interval<Integer> interval){
        delay = interval;
        plugin = PowerfulFireworks.getInstance();
    }
    public void start(){
        task= Bukkit.getScheduler()
                .runTaskLaterAsynchronously(plugin,
                        this::run,
                        rand.nextInt(delay.minimum,delay.maximum));
    }
    public void stop(){
        task.cancel();
    }

    protected void run() {

    }
}
