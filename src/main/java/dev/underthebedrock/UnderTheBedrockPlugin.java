package dev.underthebedrock;

import dev.underthebedrock.command.*;
import dev.underthebedrock.integration.IntegrationManager;
import dev.underthebedrock.storage.PlayerLocationStore;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public final class UnderTheBedrockPlugin extends JavaPlugin {
    private VoidWorldManager worlds;
    private PlayerLocationStore locations;
    private IntegrationManager integrations;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        integrations = new IntegrationManager(this);
        if (!integrations.initialize()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        locations = new PlayerLocationStore(this);
        worlds = new VoidWorldManager(this);
        register("enter", new EnterCommand(worlds, locations));
        register("leave", new LeaveCommand(locations));
        register("underthebedrock", new UnderTheBedrockCommand(worlds, integrations));
        if (getConfig().getBoolean("world.enabled-on-startup", false) && !worlds.start()) {
            getLogger().severe("Could not initialize the configured horror world.");
        }
        getLogger().info("Under The Bedrock enabled; Nova and BetterModel APIs connected.");
    }

    @Override
    public void onDisable() {
        if (locations != null) locations.flush();
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command: " + name);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabs) command.setTabCompleter(tabs);
    }
}
