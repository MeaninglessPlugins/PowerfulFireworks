package org.eu.pcraft.powerfulfireworks;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public class Permissions {
    public static final Permission ROOT = new Permission(named(), PermissionDefault.OP);

    public static final Permission CMD = new Permission(named("cmd"));
    public static final Permission CMD_TEST = new Permission(named("cmd", "test"));

    public static void register() {
        CMD.addParent(ROOT, true);
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
