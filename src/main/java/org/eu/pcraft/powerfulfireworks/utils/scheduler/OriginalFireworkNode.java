package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.Interval;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.utils.sender.SingleFirework;
import org.eu.pcraft.powerfulfireworks.utils.sender.FireworkSender;

import java.util.*;

class OriginalFireworkNode extends FireworkNode {

    int count = 1;

    Interval<Double> X = new Interval<>(0.0,0.0);
    Interval<Double> Y = new Interval<>(0.0,0.0);
    Interval<Double> Z = new Interval<>(0.0,0.0);

    FireworkSender sender;
    
    protected Interval<Double> getDoubleInterval(Map<String, Object> section, String key){
        Object obj = section.get(key);
        if(obj == null)return new Interval<>(0.0, 0.0);
        return new Interval<>(obj);
    }
    protected Double getOffset(Interval<Double> interval){
        if(Objects.equals(interval.maximum, interval.minimum)){
            return interval.maximum;
        }
        return rd.nextDouble(interval.minimum, interval.maximum);
    }
    @Override
    protected void load(FireworkScheduler scheduler, Map<String, Object> section) {
        super.load(scheduler, section);
        this.count = (int) section.getOrDefault("count", 1);
        this.X = getDoubleInterval(section,"xOff");
        this.Y = getDoubleInterval(section,"yOff");
        this.Z = getDoubleInterval(section,"zOff");
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        for (int i = 0; i < count; i++) {
            ItemStack stack = getRandomPreset();

            double xOff = getOffset(X);
            double yOff = getOffset(Y);
            double zOff = getOffset(Z);

            sender = new SingleFirework();
            // send create and add to id list
            try{
                sender.execute(flyTime, stack, config.startupLocation.clone().add(xOff, yOff, zOff), config.players);
            }catch(IllegalArgumentException e){
                if(Objects.equals(e.getMessage(), "No target specified")){
                    config.plugin.getLogger().info("No target specified");
                }else{
                    throw e;
                }
            }
        }
    }
}
