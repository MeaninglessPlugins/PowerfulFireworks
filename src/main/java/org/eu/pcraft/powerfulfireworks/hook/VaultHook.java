package org.eu.pcraft.powerfulfireworks.hook;

import lombok.Getter;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

@Getter
public class VaultHook {
    private Permission perms = null;

    public boolean setup(){
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            perms = rsp.getProvider();
        }
        return perms!=null;
    }
}
