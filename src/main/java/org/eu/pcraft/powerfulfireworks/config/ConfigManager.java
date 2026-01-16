package org.eu.pcraft.powerfulfireworks.config;

import lombok.Getter;
import org.eu.pcraft.powerfulfireworks.utils.Interval;
import org.eu.pcraft.powerfulfireworks.utils.IntervalSerializer;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;

public class ConfigManager<T> {
    CommentedConfigurationNode node;

    YamlConfigurationLoader loader;

    Class<T> configType;

    @Getter
    T configModule;

    public ConfigManager(Path src, T cm){
        loader = YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .defaultOptions(opts -> opts.serializers(build ->
                        build.register(Interval.class, IntervalSerializer.INSTANCE)))
                .path(src) // Set where we will load and save to
                .build();
        node=loader.createNode();
        configType = (Class<T>) cm.getClass();
        configModule = cm;
    }
    public void loadConfig(){
        try {
            node=loader.load();
            configModule=node.get(configType);
            saveConfig();
        }catch (ConfigurateException e){
            throw new RuntimeException(e);
        }
    }
    public void saveConfig(){
        try {
            node.set(configModule);
            loader.save(node);
        }catch (ConfigurateException e){
            throw new RuntimeException(e);
        }
    }
}
