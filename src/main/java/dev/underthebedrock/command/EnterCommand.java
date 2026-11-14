package dev.underthebedrock.command;

import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class EnterCommand implements CommandExecutor {
    private final VoidWorldManager worlds;

    public EnterCommand(VoidWorldManager worlds) {
        this.worlds = worlds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!worlds.isActive()) {
            player.sendMessage("§7There is nowhere to enter yet.");
            return true;
        }
        World world = worlds.ensureWorld();
        if (world == null) {
            player.sendMessage("§cThe horror world could not be loaded.");
            return true;
        }
        if (player.getWorld().equals(world)) {
            player.sendMessage("§7You are already there.");
            return true;
        }

        if (player.teleport(worlds.getEntranceLocation())) {
            player.sendMessage("§8You step into the empty world.");
        } else {
            player.sendMessage("§cTeleport failed. Try again in a moment.");
        }
        return true;
    }
}
