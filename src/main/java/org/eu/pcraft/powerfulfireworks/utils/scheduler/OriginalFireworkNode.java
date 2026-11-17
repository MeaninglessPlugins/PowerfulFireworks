package org.eu.pcraft.powerfulfireworks.utils.scheduler;

import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.utils.Interval;
import org.eu.pcraft.powerfulfireworks.utils.sender.SingleFirework;
import org.eu.pcraft.powerfulfireworks.utils.sender.FireworkSender;

import java.util.*;

class OriginalFireworkNode extends FireworkNode {

    int count = 1;

    Interval<Double> xRange = new Interval<>(0.0,0.0);
    Interval<Double> yRange = new Interval<>(0.0,0.0);
    Interval<Double> zRange = new Interval<>(0.0,0.0);

    FireworkSender sender;
    
    protected Interval<Double> getDoubleInterval(Map<String, Object> section, String key){
        Object obj = section.get(key);
        if(obj == null)return new Interval<>(0.0, 0.0);
        if(obj instanceof Number n)return new Interval<>(n.doubleValue(), n.doubleValue());
        if(obj instanceof List<?> list && list.size() >= 2)
            return new Interval<>(toDouble(list.get(0)), toDouble(list.get(1)));
        if(obj instanceof Map<?, ?> map)
            return new Interval<>(toDouble(map.get("minimum")), toDouble(map.get("maximum")));
        throw new IllegalArgumentException(key + " 需要数字、[最小值, 最大值] 或 {minimum, maximum}，当前为 " + obj);
    }
    private static double toDouble(Object obj){
        if(obj instanceof Number n)return n.doubleValue();
        throw new IllegalArgumentException("偏移量必须是数字，当前为 " + obj);
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
        this.count = ((Number) section.getOrDefault("count", 1)).intValue();
        this.xRange = getDoubleInterval(section,"xOff");
        this.yRange = getDoubleInterval(section,"yOff");
        this.zRange = getDoubleInterval(section,"zOff");
    }

    @Override
    public void execute(FireworkStartupConfig config) {
        for (int i = 0; i < count; i++) {
            ItemStack stack = getRandomPreset();

            double xOff = getOffset(xRange);
            double yOff = getOffset(yRange);
            double zOff = getOffset(zRange);

            sender = new SingleFirework(this.launchChance, this.explosionChance, this.velocity);
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
