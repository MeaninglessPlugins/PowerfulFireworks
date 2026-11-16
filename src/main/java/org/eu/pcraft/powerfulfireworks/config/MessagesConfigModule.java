package org.eu.pcraft.powerfulfireworks.config;

import org.eu.pcraft.powerfulfireworks.libs.config.ConfigModel;
import org.eu.pcraft.powerfulfireworks.libs.config.ConfigPath;

/**
 * messages.yml 的模型，只用来生成默认文件；实际读取由
 * {@code cn.afternode.commons.bukkit.ConfigurationLocalizations} 负责。
 *
 * <p>键名同样显式写成 {@link ConfigPath} 以保持旧版的 camelCase。</p>
 */
@ConfigModel
public class MessagesConfigModule {

    @ConfigPath("prefix")
    String prefix = "<gray>[</gray><gradient:#ffff55:#ff5555>PowerfulFireworks</gradient><gray>]</gray> ";

    @ConfigPath("commands")
    CommandsOutput commands = new CommandsOutput();

    @ConfigModel
    public static class CommandsOutput {

        @ConfigPath("noPermission")
        String noPermission = "<red>你没有权限执行此操作</red>";

        @ConfigPath("invalidArgs")
        String invalidArgs = "<red>参数无效，用法：%s</red>";

        @ConfigPath("badNum")
        String badNum = "<red>无效数字参数</red>";

        @ConfigPath("player")
        String player = "<red>此命令只允许玩家使用</red>";

        @ConfigPath("fireworks")
        FireworkCommands fireworks = new FireworkCommands();

        @ConfigModel
        public static class HelpCommand {

            @ConfigPath("header")
            String header = "--命令帮助--";

            @ConfigPath("reload")
            String reload = "重载配置文件";

            @ConfigPath("help")
            String help = "查看命令帮助";

            @ConfigPath("execute")
            String execute = "执行已定义的烟花";

            @ConfigPath("font")
            String font = "预览加载的字体";

            @ConfigPath("toggle")
            String toggle = "控制随机烟花";

            @ConfigPath("item")
            String item = "获取物品烟花";

            @ConfigPath("check")
            String check = "校验配置文件（只读，不会重载）";
        }

        @ConfigModel
        public static class CheckCommand {

            @ConfigPath("ok")
            String ok = "<green>配置校验通过，没有发现问题</green>";

            @ConfigPath("summary")
            String summary = "<yellow>配置校验完成：%s 个错误，%s 个警告，详见控制台</yellow>";
        }

        @ConfigModel
        public static class ReloadCommand {

            @ConfigPath("complete")
            String complete = "<green>重载配置文件成功!";

            @ConfigPath("failed")
            String failed = "<red>重载配置文件失败!";
        }

        @ConfigModel
        public static class FontCommand {

            @ConfigPath("available")
            String available = "已加载的字体：";

            @ConfigPath("notFound")
            String notFound = "<red>找不到目标字体</red>";

            @ConfigPath("noChar")
            String noChar = "<red>字体 %s 中没有此字符</red>";

            @ConfigPath("preview")
            String preview = "<green>字符 %s 在字体 %s 的预览</green>";
        }

        @ConfigModel
        public static class ExecuteCommand {

            @ConfigPath("schedulerNotFound")
            String schedulerNotFound = "<red>找不到烟花定义 %s</red>";

            @ConfigPath("worldName")
            String worldName = "<red>需要提供目标世界名称</red>";

            @ConfigPath("worldNotFound")
            String worldNotFound = "<red>找不到目标世界</red>";

            @ConfigPath("started")
            String started = "开始执行烟花 %s";
        }

        @ConfigModel
        public static class ToggleCommand {

            @ConfigPath("toggleMessage")
            String toggleMessage = "当前随机烟花状态: %s";
        }

        @ConfigModel
        public static class ItemCommand {

            @ConfigPath("notAllowed")
            String notAllowed = "<red>此烟花不允许使用物品触发</red>";

            @ConfigPath("success")
            String success = "已生成物品";
        }

        @ConfigModel
        public static class FireworkCommands {

            @ConfigPath("help")
            HelpCommand help = new HelpCommand();

            @ConfigPath("font")
            FontCommand font = new FontCommand();

            @ConfigPath("execute")
            ExecuteCommand execute = new ExecuteCommand();

            @ConfigPath("reload")
            ReloadCommand reload = new ReloadCommand();

            @ConfigPath("check")
            CheckCommand check = new CheckCommand();

            @ConfigPath("toggle")
            ToggleCommand toggle = new ToggleCommand();

            @ConfigPath("item")
            ItemCommand item = new ItemCommand();
        }
    }
}
