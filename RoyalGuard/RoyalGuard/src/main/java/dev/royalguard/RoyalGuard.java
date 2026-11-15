package dev.royalguard;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RoyalGuard extends JavaPlugin {
    private GuardManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new GuardManager(this);
        manager.load();
        getServer().getPluginManager().registerEvents(new GuardListener(manager), this);

        GuardCommand command = new GuardCommand(this, manager);
        PluginCommand pc = getCommand("guard");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }
        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 1L);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
            manager.despawnAll();
        }
    }
}
