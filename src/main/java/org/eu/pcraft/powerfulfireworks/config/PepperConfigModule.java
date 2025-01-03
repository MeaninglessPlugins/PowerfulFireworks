package org.eu.pcraft.powerfulfireworks.config;

import com.google.common.collect.Maps;
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
        public int min_delay = 10;
        public int max_delay = 20;
        public boolean automatic_distance = true;
        public int distance = 20;
        public int min_fly_time = 70;
        public int max_fly_time = 120;
    }

    public RandomFirework randomFirework= new RandomFirework();

    @Comment("Supports BDF files\nResolve files in the \"fonts\" directory")
    public Map<String, String> fonts = Maps.newHashMap(Map.of("example", "example.bdf"));
}
