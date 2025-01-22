package org.eu.pcraft.powerfulfireworks.config;

import com.google.common.collect.Maps;
import org.eu.pcraft.powerfulfireworks.utils.Interval;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.Map;

@ConfigSerializable
public class PepperConfigModule {
    @Comment("PowerfulFireworks配置文件") String a;
    @Comment("Written by:Pepper&H3xadecimal") String b;
    public boolean debug = false;
    @ConfigSerializable
    public static class RandomFirework{
        public boolean enabled = true;
        public boolean turnOnDefaultly = true;
        public Interval<Integer> delay=new Interval<>(10, 20);
        public boolean automaticDistance = true;
        public int distance = 20;
        public Interval<Integer> flyTime = new Interval<>(30,70);
    }

    public RandomFirework randomFirework= new RandomFirework();

    @Comment("Supports BDF files\nResolve files in the \"fonts\" directory")
    public Map<String, String> fonts = Maps.newHashMap(Map.of("example", "example.bdf"));
}
