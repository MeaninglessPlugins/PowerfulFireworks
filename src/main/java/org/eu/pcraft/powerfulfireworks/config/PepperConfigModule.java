package org.eu.pcraft.powerfulfireworks.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class PepperConfigModule {
    @Comment("PowerfulFireworks配置文件") String a;
    @Comment("Written by:Pepper&H3xadecimal") String b;
    @Comment("版本信息,请不要修改! ")
    public String configVersion = "0.0.1";
    public boolean debug = false;

}
