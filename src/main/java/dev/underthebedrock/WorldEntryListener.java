package dev.underthebedrock;

import dev.underthebedrock.item.CustomItemManager;
import dev.underthebedrock.storage.PlayerLocationStore;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Automatically places players inside the horror world while it is active. */
public final class WorldEntryListener implements Listener {
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final PlayerLocationStore locations;
    private final CustomItemManager customItems;

    public WorldEntryListener(UnderTheBedrockPlugin plugin, VoidWorldManager worlds,
                              PlayerLocationStore locations, CustomItemManager customItems) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.locations = locations;
        this.customItems = customItems;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("world.auto-join-on-connect", true) || !worlds.isActive()) return;
        Player player = event.getPlayer();
        World target = worlds.ensureWorld();
        Location entrance = worlds.getEntranceLocation();
        if (target == null || entrance == null) {
            plugin.getLogger().warning("Could not send " + player.getName() + " to Under The Bedrock: world unavailable.");
            return;
        }

        if (!player.getWorld().equals(target) && !locations.hasReturnLocation(player.getUniqueId())) {
            locations.saveReturnLocation(player, player.getLocation());
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !worlds.isActive()) return;
            Location spawn = worlds.getEntranceLocation();
            if (spawn == null) return;
            player.teleportAsync(spawn).thenAccept(success -> locations.runSync(() -> {
                if (!success && player.isOnline()) {
                    player.sendMessage("§cCould not move you into Under The Bedrock. Please contact an administrator.");
                }
            }));
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("world.auto-respawn", true) || !worlds.isActive()) return;
        World target = worlds.ensureWorld();
        Location entrance = worlds.getEntranceLocation();
        if (target == null || entrance == null) return;

        Player player = event.getPlayer();
        Location destination = entrance;
        if (player.getWorld().equals(target)) {
            Location anchor = customItems.getReconstructorSpawn(player.getUniqueId());
            if (anchor != null && anchor.getWorld() != null) destination = anchor;
        }
        event.setRespawnLocation(destination);
    }
}
