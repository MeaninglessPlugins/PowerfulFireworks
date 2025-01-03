package org.eu.pcraft.powerfulfireworks.commands;

import cn.afternode.commons.bukkit.message.MessageBuilder;
import cn.afternode.commons.bukkit.message.TabBuilder;
import lombok.Setter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.eu.pcraft.powerfulfireworks.Permissions;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Locale;

public class MainCommand extends Command {
    @Setter
    private String[] fontIdComp = PowerfulFireworks.getInstance().getFonts().keySet().toArray(new String[0]);

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
                case "font" -> this.font(sender, args);
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

    private void font(CommandSender sender, String[] args) {    // 0:"fonts" 1:<font-id>(optional) 2:<char>(optional)
        MessageBuilder mb = PowerfulFireworks.getInstance().getContext().message(sender);
        if (args.length == 1) {
            mb.localize("commands.fireworks.font.available").line().text(String.join(", ", PowerfulFireworks.getInstance().getFonts().keySet()), Color.GREEN);
        } else if (args.length == 3) {
            BitmapFont font = PowerfulFireworks.getInstance().getFonts().get(args[1].toLowerCase(Locale.ROOT));
            if (font == null) {
                mb.localize("commands.fireworks.font.not-found");   // not found
            } else {
                BitmapFont.CharBitmap character = font.getCharacter(args[2].charAt(0));
                // Render to string
                mb.localize("commands.fireworks.font.preview", String.valueOf(args[2].charAt(0)), args[1]);

                for (String aChar : character.getChars()) {
                    if (aChar == null) {
                        mb.line().localize("commands.fireworks.font.no-char", args[1]);    // no such character
                        break;
                    }
                    mb.emptyLine().text(aChar.replace('0', ' ').replace('1', '#'));
                }
            }
        } else {
            mb.localize("commands.fireworks.invalid-args", "fireworks font (font-id) (character)");
        }
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
            builder.add(args[0], "help", "reload", "font");
        } else if (args.length == 2) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            String a1 = args[1];
            if (a0.equals("font")) {
                builder.add(a1, this.fontIdComp);
            }
        }

        return builder.build();
    }
}
