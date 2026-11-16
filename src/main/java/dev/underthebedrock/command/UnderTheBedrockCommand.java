package dev.underthebedrock.command;

import dev.underthebedrock.WorldEntryListener;
import dev.underthebedrock.horror.EntityArchetype;
import dev.underthebedrock.horror.HorrorEntityController;
import dev.underthebedrock.integration.IntegrationManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class UnderTheBedrockCommand implements CommandExecutor, TabCompleter {
    private final VoidWorldManager worlds;
    private final IntegrationManager integrations;
    private final WorldEntryListener worldEntry;
    private final HorrorEntityController entities;

    public UnderTheBedrockCommand(VoidWorldManager worlds, IntegrationManager integrations,
                                  WorldEntryListener worldEntry, HorrorEntityController entities) {
        this.worlds = worlds;
        this.integrations = integrations;
        this.worldEntry = worldEntry;
        this.entities = entities;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("underthebedrock.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§7Usage: /underthebedrock <start|stop|status|entity>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length != 1) return usage(sender);
                if (worlds.start()) {
                    worldEntry.routeOnlinePlayers();
                    sender.sendMessage("§aUnder The Bedrock has taken over. Online players are being transferred; future joins and deaths are routed automatically.");
                } else sender.sendMessage("§cWorld initialization failed; check console.");
            }
            case "stop" -> {
                if (args.length != 1) return usage(sender);
                sender.sendMessage(worlds.stop() ? "§eStopped. Generated chunks were preserved."
                        : "§cPlayers are still inside. The experience cannot stop while occupied.");
            }
            case "status" -> {
                if (args.length != 1) return usage(sender);
                sender.sendMessage("§8[Under The Bedrock]");
                sender.sendMessage("§7Active: " + (worlds.isActive() ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World loaded: " + (worlds.getWorld() != null ? "§aYes" : "§cNo"));
                sender.sendMessage("§7World lockdown: " + (worlds.isActive() ? "§aEnabled" : "§cInactive"));
                sender.sendMessage("§7BetterModel: " + integrations.betterModel().statusText());
            }
            case "entity" -> handleEntity(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void handleEntity(CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            sender.sendMessage("§8[Under The Bedrock] §7Available entities:");
            sender.sendMessage("§f" + String.join("§7, §f",
                    Arrays.stream(EntityArchetype.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList()));
            sender.sendMessage("§7Use §f/underthebedrock entity spawn <id> [player]");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("spawn") && args.length <= 4) {
            EntityArchetype type;
            try {
                type = EntityArchetype.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                sender.sendMessage("§cUnknown entity. Use /underthebedrock entity list.");
                return;
            }
            Player target;
            if (args.length == 4) {
                target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    sender.sendMessage("§cThat player is not online.");
                    return;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage("§cConsole must specify a player: /underthebedrock entity spawn <id> <player>");
                return;
            }
            if (!worlds.isActive() || target.getWorld() != worlds.getWorld()) {
                sender.sendMessage("§cThe target must be inside the active Under The Bedrock world.");
                return;
            }
            if (entities.hasEncounter(target)) {
                sender.sendMessage("§cThat player already has an active apparition. Despawn it first.");
                return;
            }
            if (entities.spawnFor(target, type)) {
                sender.sendMessage("§aSpawned §f" + type.displayName() + " §afor §f" + target.getName() + "§a.");
                if (!(sender instanceof Player senderPlayer)
                        || !senderPlayer.getUniqueId().equals(target.getUniqueId())) {
                    target.sendMessage("§8Something has appeared somewhere behind you.");
                }
            } else {
                sender.sendMessage("§cCould not spawn it. Check that its BetterModel model is loaded and a safe loaded-chunk spawn point is available.");
            }
            return;
        }
        if ((args.length == 2 || args.length == 3) && args[1].equalsIgnoreCase("despawn")) {
            Player target;
            if (args.length == 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("§cThat player is not online.");
                    return;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage("§cConsole must specify a player: /underthebedrock entity despawn <player>");
                return;
            }
            sender.sendMessage(entities.despawnFor(target)
                    ? "§eRemoved the active apparition for " + target.getName() + "."
                    : "§7That player has no active apparition.");
            return;
        }
        sender.sendMessage("§7Usage: /underthebedrock entity <list|spawn <id> [player]|despawn [player]>");
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage("§7Usage: /underthebedrock <start|stop|status|entity>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("underthebedrock.admin")) return List.of();
        if (args.length == 1) return partial(List.of("start", "stop", "status", "entity"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("entity")) {
            return partial(List.of("list", "spawn", "despawn"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("entity") && args[1].equalsIgnoreCase("spawn")) {
            return partial(Arrays.stream(EntityArchetype.values())
                    .map(type -> type.name().toLowerCase(Locale.ROOT)).toList(), args[2]);
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("entity") && args[1].equalsIgnoreCase("despawn"))
                || (args.length == 4 && args[0].equalsIgnoreCase("entity") && args[1].equalsIgnoreCase("spawn"))) {
            return partial(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> partial(List<String> values, String prefix) {
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT)
                .startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
