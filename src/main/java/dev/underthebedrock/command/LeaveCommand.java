package dev.underthebedrock.command;

import dev.underthebedrock.storage.PlayerLocationStore;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class LeaveCommand implements CommandExecutor {
    private final PlayerLocationStore locations;
    public LeaveCommand(PlayerLocationStore locations) { this.locations = locations; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        Location destination = locations.takeReturnLocation(player.getUniqueId());
        if (destination == null) {
            World fallback = Bukkit.getWorlds().stream()
                .filter(w -> !w.getName().equalsIgnoreCase("under_the_bedrock"))
                .findFirst().orElse(Bukkit.getWorlds().getFirst());
            destination = fallback.getSpawnLocation();
        }
        player.teleportAsync(destination).thenAccept(ok -> {
            if (ok) player.sendMessage("§7You return to where you came from.");
            else player.sendMessage("§cCould not return you to your previous location.");
        });
        return true;
    }
}
