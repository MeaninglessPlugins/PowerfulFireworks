package org.eu.pcraft.powerfulfireworks.commands;

import cn.afternode.commons.bukkit.BukkitPluginContext;
import cn.afternode.commons.bukkit.message.MessageBuilder;
import cn.afternode.commons.bukkit.message.TabBuilder;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.eu.pcraft.powerfulfireworks.Permissions;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.config.DiagnosticReporter;
import org.eu.pcraft.powerfulfireworks.runtime.BukkitScriptHost;
import org.eu.pcraft.powerfulfireworks.script.CompiledScript;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkScheduler;
import org.eu.pcraft.powerfulfireworks.utils.scheduler.FireworkStartupConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

@Setter
public class MainCommand extends Command {
    private final BukkitPluginContext ctx;
    private String[] fontIdComp;
    private String[] fireworkComp;
    private String[] fireworkItemComp;
    private int runSeq;

    public MainCommand(PowerfulFireworks plugin) {
        super("fireworks");
        this.setPermission(Permissions.CMD_FIREWORKS.getName());

        this.ctx = plugin.getContext();
        this.fontIdComp = plugin.getFonts().keySet().toArray(new String[0]);
        Map<String, FireworkScheduler> sched = plugin.getSchedulers();
        Set<String> ids = sched.keySet();
        this.fireworkComp = ids.toArray(new String[0]);
        this.fireworkItemComp = ids.stream()
                .filter(id -> sched.get(id).isAllowActivationByItems())
                .toArray(String[]::new);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS)) { // no permission
            this.ctx.message(sender)
                    .localize("commands.noPermission")
                    .send();
        } else if (args.length == 0) {  // requires args
            this.help(sender);
        } else {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> this.help(sender);
                case "font" -> this.font(sender, args);
                case "execute" -> this.execute(sender, args);
                case "reload" -> this.reload(sender);
                case "check" -> this.check(sender);
                case "toggle" -> this.toggle(sender);
                case "item" -> this.item(sender, args);
                case "script" -> this.script(sender, args);
                case "stop" -> this.stop(sender);
                default -> this.help(sender);
            }
        }

        return true;
    }

    private void toggle(CommandSender sender){
        if(!(sender instanceof Player)){
            this.ctx.message(sender)
                    .localize("commands.noPermission")
                    .send();
            return;
        }
        if(!sender.hasPermission(Permissions.CMD_FIREWORKS_TOGGLE)){
            this.ctx.message(sender)
                    .localize("commands.noPermission")
                    .send();
            return;
        }
        PowerfulFireworks plugin = PowerfulFireworks.getInstance();
        plugin.toggleFirework((Player) sender, !plugin.getPlayerSet().contains(sender));
        this.ctx.message(sender)
                .localize("commands.fireworks.toggle.toggleMessage", plugin.getPlayerSet().contains(sender)?"<green>ON":"<red>OFF")
                .send();
    }
    private void help(CommandSender sender) {
        MessageBuilder mb = this.ctx.message(sender);
        mb.localize("commands.fireworks.help.header")
                .line().text("help - ").localize("commands.fireworks.help.help");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_RELOAD))
            mb.line().text("reload - ").localize("commands.fireworks.help.reload");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_CHECK))
            mb.line().text("check - ").localize("commands.fireworks.help.check");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_EXECUTE))
            mb.line().text("execute - ").localize("commands.fireworks.help.execute");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_FONT))
            mb.line().text("font - ").localize("commands.fireworks.help.font");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_TOGGLE))
            mb.line().text("toggle - ").localize("commands.fireworks.help.toggle");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_ITEM))
            mb.line().text("item - ").localize("commands.fireworks.help.item");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_SCRIPT))
            mb.line().text("script - ").localize("commands.fireworks.help.script");
        if (sender.hasPermission(Permissions.CMD_FIREWORKS_STOP))
            mb.line().text("stop - ").localize("commands.fireworks.help.stop");
        mb.send();
    }

    private void font(CommandSender sender, String[] args) {    // 0:"fonts" 1:<font-id>(optional) 2:<char>(optional)
        MessageBuilder mb = this.ctx.message(sender);
        if (args.length == 1) {
            mb.localize("commands.fireworks.font.available").line().text(String.join(", ", PowerfulFireworks.getInstance().getFonts().keySet()), Color.GREEN);
        } else if (args.length == 3) {
            if (sender.hasPermission(Permissions.CMD_FIREWORKS_FONT)) {
                BitmapFont font = PowerfulFireworks.getInstance().getFonts().get(args[1].toLowerCase(Locale.ROOT));
                if (font == null) {
                    mb.localize("commands.fireworks.font.notFound");   // not found
                } else {
                    BitmapFont.CharBitmap character = font.getCharacter(args[2].charAt(0));
                    // Render to string
                    mb.localize("commands.fireworks.font.preview", String.valueOf(args[2].charAt(0)), args[1]);

                    for (String aChar : character.chars()) {
                        if (aChar == null) {
                            mb.line().localize("commands.fireworks.font.noChar", args[1]);    // no such character
                            break;
                        }
                        mb.emptyLine().text(aChar.replace('0', ' ').replace('1', '#'));
                    }
                }
            } else {    // no permission
                mb.localize("commands.noPermission");
            }
        } else {
            mb.localize("commands.invalidArgs", "fireworks font (font-id) (character)");
        }
        mb.send();
    }

    private void execute(CommandSender sender, String[] args) { // 0:"execute" 1:<scheduler-id> 2:<x> 3:<y> 4:<z> 5:<world>(optional)
        MessageBuilder mb = this.ctx.message(sender);
        if (args.length < 5) {
            mb.localize("commands.invalidArgs", "fireworks execute <firework> <x> <y> <z> (world)");
        } else if (sender.hasPermission(Permissions.CMD_FIREWORKS_EXECUTE)) {
            FireworkScheduler scheduler = PowerfulFireworks.getInstance().getSchedulers().get(args[1]);
            if (scheduler == null)
                mb.localize("commands.fireworks.execute.schedulerNotFound", args[1]);
            else {
                double x, y, z;
                try {
                    x = Double.parseDouble(args[2]);
                } catch (NumberFormatException t) {
                    mb.localize("commands.badNum").send();
                    return;
                }
                try {
                    y = Double.parseDouble(args[3]);
                } catch (NumberFormatException t) {
                    mb.localize("commands.badNum").send();
                    return;
                }
                try {
                    z = Double.parseDouble(args[4]);
                } catch (NumberFormatException t) {
                    mb.localize("commands.badNum").send();
                    return;
                }
                World world = null;
                if (sender instanceof Player player)    // use player's world
                    world = player.getWorld();
                if (args.length >= 6) {
                    world = Bukkit.getWorld(args[5]);
                    if (world == null) {    // invalid world
                        mb.localize("commands.fireworks.execute.worldNotFound").send();
                        return;
                    }
                } else if (!(sender instanceof Player)) {    // not player and no world provided
                    mb.localize("commands.fireworks.execute.worldName").send();
                    return;
                }

                FireworkStartupConfig config = new FireworkStartupConfig(new Location(world, x, y, z), Arrays.asList(world.getPlayers().toArray(new Player[0])));
                scheduler.execute(config);
                mb.localize("commands.fireworks.execute.started", scheduler.getId());
            }
        } else {
            mb.localize("commands.noPermission");
        }
        mb.send();
    }

    private void reload(CommandSender sender) {
        MessageBuilder mb = this.ctx.message(sender);
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS_RELOAD)) {
            mb.localize("commands.noPermission");
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

    /** 只校验、不重载：不改文件、不替换运行时状态，详细诊断输出到控制台。 */
    private void check(CommandSender sender) {
        MessageBuilder mb = this.ctx.message(sender);
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS_CHECK)) {
            mb.localize("commands.noPermission");
        } else {
            try {
                DiagnosticReporter report = PowerfulFireworks.getInstance().checkConfigurations();
                if (report.errors() == 0 && report.warnings() == 0) {
                    mb.localize("commands.fireworks.check.ok");
                } else {
                    mb.localize("commands.fireworks.check.summary",
                            String.valueOf(report.errors()), String.valueOf(report.warnings()));
                }
            } catch (Throwable t) {
                mb.localize("commands.fireworks.reload.failed");
            }
        }
        mb.send();
    }

    /**
     * 运行一个脚本演出。
     *
     * <p>原点取执行者当前位置，接收者是同世界、视距范围内的玩家——和随机烟花用的是同一套范围，
     * 避免出现"看得见烟花的地方收不到包"。
     */
    private void script(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS_SCRIPT)) {
            this.ctx.message(sender).localize("commands.noPermission").send();
            return;
        }
        if (!(sender instanceof Player player)) {
            this.ctx.message(sender).localize("commands.fireworks.script.playerOnly").send();
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("run")) {
            this.ctx.message(sender).localize("commands.fireworks.script.usage").send();
            return;
        }

        Long seed = null;
        if (args.length > 3) {
            try {
                seed = Long.parseLong(args[3]);
            } catch (NumberFormatException e) {
                this.ctx.message(sender)
                        .localize("commands.fireworks.script.badSeed", args[3])
                        .send();
                return;
            }
        }

        PowerfulFireworks plugin = PowerfulFireworks.getInstance();
        CompiledScript compiled = plugin.getScripts().get(args[2]);
        if (compiled == null) {
            this.ctx.message(sender)
                    .localize("commands.fireworks.script.notFound", args[2])
                    .send();
            return;
        }

        Location origin = player.getLocation();
        double max = Bukkit.getViewDistance() * 16.0;
        List<Player> receivers = origin.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(origin) <= max * max)
                .toList();

        String id = args[2] + "#" + (++this.runSeq);
        try {
            plugin.getRuntimeService().start(id, compiled,
                    new BukkitScriptHost(plugin, plugin.getSLF4JLogger(), origin, receivers), Map.of(), seed);
        } catch (RuntimeException e) {
            this.ctx.message(sender)
                    .localize("commands.fireworks.script.failed", args[2], String.valueOf(e.getMessage()))
                    .send();
            plugin.getSLF4JLogger().warn("脚本 {} 启动失败", args[2], e);
            return;
        }

        if (seed == null) {
            this.ctx.message(sender)
                    .localize("commands.fireworks.script.started", args[2], String.valueOf(receivers.size()))
                    .send();
        } else {
            this.ctx.message(sender)
                    .localize("commands.fireworks.script.startedWithSeed",
                            args[2], String.valueOf(seed), String.valueOf(receivers.size()))
                    .send();
        }
    }

    /** 停止所有正在跑的脚本演出。 */
    private void stop(CommandSender sender) {
        if (!sender.hasPermission(Permissions.CMD_FIREWORKS_STOP)) {
            this.ctx.message(sender).localize("commands.noPermission").send();
            return;
        }
        int count = PowerfulFireworks.getInstance().getRuntimeService().stopAll();
        this.ctx.message(sender)
                .localize("commands.fireworks.stop.stopped", String.valueOf(count))
                .send();
    }

    private void item(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 2) {
                FireworkScheduler sched = PowerfulFireworks.getInstance().getSchedulers().get(args[1]);
                MessageBuilder message = this.ctx.message(sender);
                if (sched == null) {
                    message.localize("commands.fireworks.execute.schedulerNotFound", args[1]);
                } else if (!sched.isAllowActivationByItems()) {
                    message.localize("commands.fireworks.item.notAllowed");
                } else {
                    ItemStack item = sched.createItem();
                    player.getInventory().addItem(item);
                    message.localize("commands.fireworks.item.success");
                }
                message.send();
            } else {
                this.ctx.message(sender)
                        .localize("commands.player")
                        .send();
            }
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        TabBuilder builder = new TabBuilder();
        if (args.length == 1) {
            builder.add(args[0], "help", "execute", "reload", "font", "toggle", "check", "item", "script", "stop");
        } else if (args.length == 2) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            String a1 = args[1];
            switch (a0) {
                case "font" -> builder.add(a1, this.fontIdComp);
                case "execute" -> builder.add(a1, this.fireworkComp);
                case "item" -> builder.add(a1, this.fireworkItemComp);
                case "script" -> builder.add(a1, "run");
            }
        }

        return builder.build();
    }
}
