package org.eu.pcraft.powerfulfireworks.commands;

import cn.afternode.commons.bukkit.message.MessageBuilder;
import cn.afternode.commons.bukkit.message.TabBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.eu.pcraft.powerfulfireworks.Permissions;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class MainCommand extends Command {
    public MainCommand() {
        super("fireworks");
        this.setPermission(Permissions.CMD_FIREWORKS.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS)) { // no permission
            PowerfulFireworks.getInstance().getContext().message(sender)
                    .localize("commands.no-permission")
                    .send();
        } else if (args.length == 0) {  // requires args
            this.help(sender);
        } else {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> this.help(sender);
                case "reload" -> this.reload(sender);
            }
        }

        return true;
    }

    private void help(CommandSender sender) {
        MessageBuilder mb = PowerfulFireworks.getInstance().getContext().message(sender);
        mb.localize("commands.fireworks.help.header")
                .line().text("help - ").localize("commands.fireworks.help.help");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_RELOAD))
            mb.line().text("reload - ").localize("commands.fireworks.help.reload");
        mb.send();
    }

    private void reload(CommandSender sender) {
        MessageBuilder mb = PowerfulFireworks.getInstance().getContext().message(sender);
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS_RELOAD)) {
            mb.localize("commands.no-permission");
        } else {
            try {
                PowerfulFireworks.getInstance().loadConfigurations();
                mb.localize("commands.fireworks.reload.complete");
            } catch (Throwable t) {
                mb.localize("commands.fireworks.reload.failed");
            }
        }
        mb.send();
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        TabBuilder builder = new TabBuilder();
        if (args.length == 1) {
            builder.add(args[0], "help", "reload");
        }

        return builder.build();
    }
}
