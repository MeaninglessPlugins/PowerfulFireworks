package org.eu.pcraft.powerfulfireworks;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public class Permissions {
    public static final Permission ROOT = new Permission(named(), PermissionDefault.OP);

    public static final Permission CMD = new Permission(named("cmd"));
    public static final Permission CMD_FIREWORKS = new Permission(named("cmd", "fireworks"));
    public static final Permission CMD_FIREWORKS_FONT = new Permission(named("cmd", "fireworks", "font"));
    public static final Permission CMD_FIREWORKS_EXECUTE = new Permission(named("cmd", "fireworks", "execute"));
    public static final Permission CMD_FIREWORKS_RELOAD = new Permission(named("cmd", "fireworks", "reload"));
    public static final Permission CMD_TEST = new Permission(named("cmd", "test"));

    public static void register() {
        CMD.addParent(ROOT, true);
        CMD_FIREWORKS.addParent(CMD, true);
        CMD_FIREWORKS_FONT.addParent(CMD_FIREWORKS, true);
        CMD_FIREWORKS_EXECUTE.addParent(CMD_FIREWORKS, true);
        CMD_FIREWORKS_RELOAD.addParent(CMD_FIREWORKS, true);
        CMD_TEST.addParent(CMD, true);
    }

    private static String named(String... nodes) {
        StringBuilder sb = new StringBuilder("powerfullfireworks");
        for (String node : nodes) {
            sb.append('.').append(node);
        }
        return sb.toString();
    }
}
