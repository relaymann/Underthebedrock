package dev.underthebedrock.command;

import dev.underthebedrock.integration.IntegrationManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.command.*;
import java.util.List;

public final class UnderTheBedrockCommand implements CommandExecutor, TabCompleter {
    private final VoidWorldManager worlds;
    private final IntegrationManager integrations;
    public UnderTheBedrockCommand(VoidWorldManager worlds, IntegrationManager integrations) {
        this.worlds = worlds; this.integrations = integrations;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("underthebedrock.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length != 1) { sender.sendMessage("§7Usage: /underthebedrock <start|stop|status>"); return true; }
        switch (args[0].toLowerCase()) {
            case "start" -> sender.sendMessage(worlds.start() ? "§aUnder The Bedrock is active. New joins and respawns will be sent to its entrance automatically." : "§cWorld initialization failed; check console.");
            case "stop" -> sender.sendMessage(worlds.stop() ? "§eStopped. Generated chunks were preserved." : "§cPlayers are still inside; have them use /leave.");
            case "status" -> {
                sender.sendMessage("§8[Under The Bedrock]");
                sender.sendMessage("§7Active: " + (worlds.isActive() ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World loaded: " + (worlds.getWorld() != null ? "§aYes" : "§cNo"));
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
