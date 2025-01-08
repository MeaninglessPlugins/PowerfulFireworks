package org.eu.pcraft.powerfulfireworks.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class MessagesConfigModule {
    String prefix = "<gray>[</gray><gold>烟花</gold><gray>]</gray> ";
    @ConfigSerializable
    public static class CommandsOutput {
        String noPermission = "<red>你没有权限执行此操作</red>";
        String invalidArgs = "<red>参数无效，用法：%s</red>";
        String badNum = "<red>无效数字参数</red>";

        @ConfigSerializable
        public static class HelpCommand{
            String header = "--命令帮助--";
            String reload = "重载配置文件";
            String help = "查看命令帮助";
            String execute = "执行已定义的烟花";
            String font = "预览加载的字体";
        }

        @ConfigSerializable
        public static class ReloadCommand{
            String complete = "<green>重载配置文件成功!";
            String failed = "<red>重载配置文件失败!";
        }

        @ConfigSerializable
        public static class FontCommand {
            String available = "已加载的字体：";
            String notFound = "<red>找不到目标字体</red>";
            String noChar = "<red>字体 %s 中没有此字符</red>";
            String preview = "<green>字符 %s 在字体 %s 的预览</green>";
        }

        @ConfigSerializable
        public static class ExecuteCommand {
            String schedulerNotFound = "<red>找不到烟花定义 %s</red>";
            String worldName = "<red>需要提供目标世界名称</red>";
            String worldNotFound = "<red>找不到目标世界</red>";
            String started = "开始执行烟花 %s";
        }

        @ConfigSerializable
        public static class FireworkCommands{
            HelpCommand help = new HelpCommand();
            FontCommand font = new FontCommand();
            ExecuteCommand execute = new ExecuteCommand();
            ReloadCommand reload = new ReloadCommand();
        }

        FireworkCommands fireworks = new FireworkCommands();
    }
    CommandsOutput commands = new CommandsOutput();
}
