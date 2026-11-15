package dev.underthebedrock.command;

import dev.underthebedrock.WorldEntryListener;
import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.integration.IntegrationManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.command.*;
import java.util.List;

public final class UnderTheBedrockCommand implements CommandExecutor, TabCompleter {
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final IntegrationManager integrations;
    private final WorldEntryListener worldEntry;

    public UnderTheBedrockCommand(UnderTheBedrockPlugin plugin, VoidWorldManager worlds, IntegrationManager integrations, WorldEntryListener worldEntry) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.integrations = integrations;
        this.worldEntry = worldEntry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("underthebedrock.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 1 || args.length > 2) { sender.sendMessage("§7Usage: /underthebedrock <start|stop|status|reset [confirm]>"); return true; }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (worlds.start()) {
                    worldEntry.enableLockdown();
                    worldEntry.routeOnlinePlayers();
                    sender.sendMessage("§aUnder The Bedrock has taken over. Online players are being transferred; future joins and deaths are routed automatically.");
                } else sender.sendMessage("§cWorld initialization failed; check console.");
            }
            case "stop" -> sender.sendMessage(worldEntry.stopExperienceForAdmin()
                ? "§eStopped. Players were evacuated by the administrator; generated chunks and progression were preserved."
                : "§cCould not safely stop the experience. Check that another world is available for administrative evacuation.");
            case "reset" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    sender.sendMessage("§cFULL RESET WARNING: this permanently clears the Under The Bedrock world and all exploration, event, structure, entity, facility, and horror-memory progress.");
                    sender.sendMessage("§7A timestamped backup is created first. Plugin config and original player-inventory recovery backups are preserved.");
                    sender.sendMessage("§eRun §f/underthebedrock reset confirm §eto schedule the reset and server restart.");
                } else if (plugin.requestFullReset()) {
                    sender.sendMessage("§aReset scheduled. The server will shut down safely; the world and plugin progression will be backed up and cleared on the next startup.");
                } else sender.sendMessage("§cCould not schedule reset. Check the console and plugin folder permissions.");
            }
            case "status" -> {
                sender.sendMessage("§8[Under The Bedrock]");
                sender.sendMessage("§7Active: " + (worlds.isActive() ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World loaded: " + (worlds.getWorld() != null ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World lockdown: " + (worlds.isActive() ? "§aEnabled" : "§cInactive"));
                sender.sendMessage("§7BetterModel: " + integrations.betterModel().statusText());
            }
            default -> sender.sendMessage("§7Usage: /underthebedrock <start|stop|status|reset [confirm]>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("underthebedrock.admin")) return List.of();
        if (args.length == 1) return List.of("start", "stop", "status", "reset").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) return List.of("confirm");
        return List.of();
    }
}
