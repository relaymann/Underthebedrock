package dev.underthebedrock.command;

import dev.underthebedrock.WorldEntryListener;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class EnterCommand implements CommandExecutor {
    private final WorldEntryListener worldEntry;

    public EnterCommand(WorldEntryListener worldEntry) {
        this.worldEntry = worldEntry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!worldEntry.enterExperience(player)) {
            player.sendMessage("§cUnder The Bedrock could not safely admit you. Check that the experience is active and your legacy inventory can be backed up.");
        } else {
            player.sendMessage("§8Your entry request is being processed.");
        }
        return true;
    }
}
