package dev.underthebedrock.command;

import dev.underthebedrock.WorldEntryListener;
import dev.underthebedrock.integration.IntegrationManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.command.*;
import java.util.List;

public final class UnderTheBedrockCommand implements CommandExecutor, TabCompleter {
    private final VoidWorldManager worlds;
    private final IntegrationManager integrations;
    private final WorldEntryListener worldEntry;
    public UnderTheBedrockCommand(VoidWorldManager worlds, IntegrationManager integrations, WorldEntryListener worldEntry) {
        this.worlds = worlds; this.integrations = integrations; this.worldEntry = worldEntry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("underthebedrock.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length != 1) { sender.sendMessage("§7Usage: /underthebedrock <start|stop|status>"); return true; }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (worlds.start()) {
                    worldEntry.routeOnlinePlayers();
                    sender.sendMessage("§aUnder The Bedrock has taken over. Online players are being transferred; future joins and deaths are routed automatically.");
                } else sender.sendMessage("§cWorld initialization failed; check console.");
            }
            case "stop" -> sender.sendMessage(worlds.stop() ? "§eStopped. Generated chunks were preserved." : "§cPlayers are still inside. The experience cannot stop while occupied.");
            case "status" -> {
                sender.sendMessage("§8[Under The Bedrock]");
                sender.sendMessage("§7Active: " + (worlds.isActive() ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World loaded: " + (worlds.getWorld() != null ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World lockdown: " + (worlds.isActive() ? "§aEnabled" : "§cInactive"));
                sender.sendMessage("§7Nova: " + integrations.nova().statusText());
                sender.sendMessage("§7BetterModel: " + integrations.betterModel().statusText());
            }
            default -> sender.sendMessage("§7Usage: /underthebedrock <start|stop|status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("underthebedrock.admin")) {
            return List.of("start", "stop", "status").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
