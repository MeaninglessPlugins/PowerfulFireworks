package org.eu.pcraft.powerfulfireworks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.util.Random;

public class PepperRollTimer {
    int minDelay,maxDelay;
    PowerfulFireworks plugin;
    BukkitTask task;
    Random rand=new Random();
    public PepperRollTimer(int mindelay, int maxdelay, PowerfulFireworks javaPlugin){
        minDelay=mindelay;
        maxDelay=maxdelay;
        plugin=javaPlugin;
    }
    public void start(){
        task= Bukkit.getScheduler()
                .runTaskLaterAsynchronously(plugin,
                        this::run,
                        rand.nextInt(minDelay,maxDelay));
    }
    public void stop(){
        task.cancel();
    }

    protected void run() {

    }
}
