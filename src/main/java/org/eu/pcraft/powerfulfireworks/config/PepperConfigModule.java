package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.Codec;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigComment;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigHeader;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigModel;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigPath;
import org.eu.pcraft.powerfulfireworks.utils.Interval;

import java.util.List;
import java.util.Map;

/**
 * config.yml 的模型。
 *
 * <p>所有键名都显式写成 {@link ConfigPath}，保持和旧版一致的 camelCase；不写的话框架会默认
 * 转成 kebab-case，老配置就全部对不上了。</p>
 */
@ConfigModel
@ConfigHeader({"PowerfulFireworks 配置文件", "Written by: Pepper & Zyklone"})
public class PepperConfigModule {

    @ConfigPath("debug")
    @ConfigComment("调试模式：把节点执行情况写进日志")
    public boolean debug = false;

    @ConfigPath("randomFirework")
    public RandomFirework randomFirework = new RandomFirework();

    @ConfigPath("fonts")
    @ConfigComment({"字体 ID 到文件名的映射", "文件放在插件目录的 fonts 文件夹里，只支持 BDF 格式"})
    public Map<String, String> fonts = Map.of("example", "example.bdf");

    @ConfigModel
    public static class RandomFireworkText {

        @ConfigPath("enabled")
        @ConfigComment("是否允许随机生成文本烟花")
        public boolean enabled = true;

        @ConfigPath("size")
        @ConfigComment("点阵的间距（格）")
        public double size = 1.0;

        @ConfigPath("font")
        @ConfigComment("使用的字体 ID，对应 fonts 段的键")
        public String font = "example";

        @ConfigPath("texts")
        @ConfigComment("随机挑选的文本")
        public List<String> texts = List.of("Hello World!");

        @ConfigPath("gap")
        @ConfigComment("字符之间的间隔（像素）")
        public int gap = 2;

        @ConfigPath("chance")
        @ConfigComment("每个随机烟花有这个概率变成文本烟花")
        public double chance = 0.05;

        @ConfigPath("launchChance")
        @ConfigComment("单个火箭发射声（嗖）的播放概率：0 全部静音，1 全部发声")
        public double launchChance = 0.05;

        @ConfigPath("explosionChance")
        @ConfigComment("单个火箭爆炸声（砰）的播放概率：0 全部静音，1 全部发声")
        public double explosionChance = 0.1;
    }

    @ConfigModel
    public static class RandomFirework {

        @ConfigPath("enabled")
        @ConfigComment("是否启用随机烟花")
        public boolean enabled = true;

        @ConfigPath("turnOnByDefault")
        @ConfigComment("服务器启动时是否默认开启随机烟花")
        public boolean turnOnByDefault = true;

        @ConfigPath("delay")
        @Codec(IntIntervalCodec.class)
        @ConfigComment("两轮随机烟花之间的间隔（tick）")
        public Interval<Integer> delay = new Interval<>(10, 20);

        @ConfigPath("number")
        @Codec(IntIntervalCodec.class)
        @ConfigComment("每轮为每个玩家生成的烟花数量")
        public Interval<Integer> number = new Interval<>(2, 5);

        @ConfigPath("automaticDistance")
        @ConfigComment("是否按服务端视距自动计算生成半径")
        public boolean automaticDistance = true;

        @ConfigPath("distance")
        @ConfigComment("automaticDistance 为 false 时使用的手动半径（格）")
        public int distance = 20;

        @ConfigPath("flyTime")
        @Codec(IntIntervalCodec.class)
        @ConfigComment("火箭升空多久后引爆（tick），即爆炸高度")
        public Interval<Integer> flyTime = new Interval<>(25, 50);

        @ConfigPath("launchChance")
        @ConfigComment("单个烟花发射声（嗖）的播放概率：0 全部静音，1 全部发声")
        public double launchChance = 0.1;

        @ConfigPath("explosionChance")
        @ConfigComment("单个烟花爆炸声（砰）的播放概率：0 全部静音，1 全部发声")
        public double explosionChance = 0.2;

        @ConfigPath("blacklistWorlds")
        @ConfigComment("不生成随机烟花的世界")
        public List<String> blacklistWorlds = List.of("world_nether");

        @ConfigPath("text")
        public RandomFireworkText text = new RandomFireworkText();
    }
}
