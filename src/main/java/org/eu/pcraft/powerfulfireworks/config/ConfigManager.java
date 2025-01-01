package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    CommentedConfigurationNode node;

    YamlConfigurationLoader loader;
    public PepperConfigModule configModule;
    public ConfigManager(Path src,PowerfulFireworks inst){
        if (Files.notExists(src)){
            inst.saveResource("config.yml",false);
        }
        loader = YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .path(src) // Set where we will load and save to
                .build();
        node=loader.createNode();
    }
    public void loadConfig(){
        try {
            node=loader.load();
            configModule=node.get(PepperConfigModule.class);
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
