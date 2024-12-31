package org.eu.pcraft.powerfulfireworks.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;

public class ConfigManager {
    CommentedConfigurationNode node;
    YamlConfigurationLoader loader;
    PepperConfigModule configModule;
    public ConfigManager(Path src){
        loader = YamlConfigurationLoader.builder()
                .indent(2)
                .path(src) // Set where we will load and save to
                .build();
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
            node.set(PepperConfigModule.class);
            loader.save(node);
        }catch (ConfigurateException e){
            throw new RuntimeException(e);
        }
    }
}
