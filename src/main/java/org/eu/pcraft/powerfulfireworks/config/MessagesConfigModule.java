package org.eu.pcraft.powerfulfireworks.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class MessagesConfigModule {
    String prefix = "<gray>[</gray><gold>烟花</gold><gray>]</gray> ";
    @ConfigSerializable
    public static class CommandsOutput {
        String noPermission = "<red>你没有权限执行此操作</red>";
        @ConfigSerializable
        public static class HelpCommand{
            String header = "--命令帮助--";
            String reload = "重载配置文件";
            String help = "查看命令帮助";
        }
        @ConfigSerializable
        public static class ReloadCommand{
            String complete = "<green>重载配置文件成功!";
            String failed = "<red>重载配置文件失败!";
        }
        @ConfigSerializable
        public static class FireworkCommands{
            HelpCommand help = new HelpCommand();
            ReloadCommand reload = new ReloadCommand();
        }
        FireworkCommands fireworks = new FireworkCommands();
    }
    CommandsOutput commands = new CommandsOutput();
}
