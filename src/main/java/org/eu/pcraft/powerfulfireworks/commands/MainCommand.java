package org.eu.pcraft.powerfulfireworks.commands;

import cn.afternode.commons.bukkit.message.MessageBuilder;
import cn.afternode.commons.bukkit.message.TabBuilder;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.eu.pcraft.powerfulfireworks.Permissions;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.hook.VaultHook;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkStartupConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Setter
public class MainCommand extends Command {
    private String[] fontIdComp = PowerfulFireworks.getInstance().getFonts().keySet().toArray(new String[0]);
    private String[] fireworkComp = PowerfulFireworks.getInstance().getSchedulers().keySet().toArray(new String[0]);

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
                case "execute" -> this.execute(sender, args);
                case "reload" -> this.reload(sender);
                case "toggle" -> this.toggle(sender);
                default -> this.help(sender);
            }
        }

        return true;
    }

    private void toggle(CommandSender sender){
        if(!(sender instanceof Player)){
            PowerfulFireworks.getInstance().getContext().message(sender)
                    .localize("commands.no-permission")
                    .send();
            return;
        }
        if(!sender.hasPermission(Permissions.CMD_FIREWORKS_TOGGLE)){
            PowerfulFireworks.getInstance().getContext().message(sender)
                    .localize("commands.no-permission")
                    .send();
            return;
        }
        VaultHook vaultHook=PowerfulFireworks.getInstance().getVaultHook();
        if(vaultHook.getPerms().has((Player) sender, Permissions.SWITCHES_RANDOMFIREWORKS.getName())){
            vaultHook.getPerms().playerRemove((Player) sender, Permissions.SWITCHES_RANDOMFIREWORKS.getName());
        }else{
            vaultHook.getPerms().playerAdd((Player) sender, Permissions.SWITCHES_RANDOMFIREWORKS.getName());
        }
        PowerfulFireworks.getInstance().getContext().message(sender)
                .localize("commands.fireworks.toggle.toggle-message", sender.hasPermission(Permissions.SWITCHES_RANDOMFIREWORKS)?"<green>ON":"<red>OFF")
                .send();
    }
    private void help(CommandSender sender) {
        MessageBuilder mb = PowerfulFireworks.getInstance().getContext().message(sender);
        mb.localize("commands.fireworks.help.header")
                .line().text("help - ").localize("commands.fireworks.help.help");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_RELOAD))
            mb.line().text("reload - ").localize("commands.fireworks.help.reload");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_EXECUTE))
            mb.line().text("execute - ").localize("commands.fireworks.help.execute");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_FONT))
            mb.line().text("font - ").localize("commands.fireworks.help.font");
        mb.send();
    }

    private void font(CommandSender sender, String[] args) {    // 0:"fonts" 1:<font-id>(optional) 2:<char>(optional)
        MessageBuilder mb = PowerfulFireworks.getInstance().getContext().message(sender);
        if (args.length == 1) {
            mb.localize("commands.fireworks.font.available").line().text(String.join(", ", PowerfulFireworks.getInstance().getFonts().keySet()), Color.GREEN);
        } else if (args.length == 3) {
            if (sender.hasPermission(Permissions.CMD_FIREWORKS_FONT)) {
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
            } else {    // no permission
                mb.localize("commands.no-permission");
            }
        } else {
            mb.localize("commands.fireworks.invalid-args", "fireworks font (font-id) (character)");
        }
        mb.send();
    }

    private void execute(CommandSender sender, String[] args) { // 0:"execute" 1:<scheduler-id> 2:<x> 3:<y> 4:<z> 5:<world>(optional)
        MessageBuilder mb = PowerfulFireworks.getInstance().getContext().message(sender);
        if (args.length < 5) {
            mb.localize("commands.invalid-args", "fireworks execute <firework> <x> <y> <z> (world)");
        } else if (sender.hasPermission(Permissions.CMD_FIREWORKS_EXECUTE)) {
            FireworkScheduler scheduler = PowerfulFireworks.getInstance().getSchedulers().get(args[1]);
            if (scheduler == null)
                mb.localize("commands.fireworks.scheduler-not-found");
            else {
                double x, y, z;
                try {
                    x = Double.parseDouble(args[2]);
                } catch (NumberFormatException t) {
                    mb.localize("commands.bad-num").send();
                    return;
                }
                try {
                    y = Double.parseDouble(args[3]);
                } catch (NumberFormatException t) {
                    mb.localize("commands.bad-num").send();
                    return;
                }
                try {
                    z = Double.parseDouble(args[4]);
                } catch (NumberFormatException t) {
                    mb.localize("commands.bad-num").send();
                    return;
                }
                World world = null;
                if (sender instanceof Player player)    // use player's world
                    world = player.getWorld();
                if (args.length >= 6) {
                    world = Bukkit.getWorld(args[5]);
                    if (world == null) {    // invalid world
                        mb.localize("commands.fireworks.execute.world-not-found").send();
                        return;
                    }
                } else if (!(sender instanceof Player)) {    // not player and no world provided
                    mb.localize("commands.fireworks.execute.world-name").send();
                    return;
                }

                FireworkStartupConfig config = new FireworkStartupConfig(new Location(world, x, y, z), Arrays.asList(Bukkit.getOnlinePlayers().toArray(new Player[0])));
                scheduler.execute(config);
                mb.localize("commands.fireworks.execute.started", scheduler.getId());
            }
        } else {
            mb.localize("commands.no-permission");
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
                PowerfulFireworks.getInstance().applyConfigurations();
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
            builder.add(args[0], "help", "execute", "reload", "font", "toggle");
        } else if (args.length == 2) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            String a1 = args[1];
            if (a0.equals("font")) {
                builder.add(a1, this.fontIdComp);
            } else if (a0.equals("execute")) {
                builder.add(a1, this.fireworkComp);
            }
        }

        return builder.build();
    }
}
