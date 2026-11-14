package dev.underthebedrock;

import dev.underthebedrock.item.CustomItemManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/** Enforces the horror world as the only player destination while the experience is active. */
public final class WorldEntryListener implements Listener {
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final CustomItemManager customItems;
    private final File isolationFile;
    private final YamlConfiguration isolated;

    public WorldEntryListener(UnderTheBedrockPlugin plugin, VoidWorldManager worlds, CustomItemManager customItems) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.customItems = customItems;
        isolationFile = new File(plugin.getDataFolder(), "player-isolation.yml");
        isolated = YamlConfiguration.loadConfiguration(isolationFile);
    }

    public void routeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> route(player, true));
        }
    }

    private void route(Player player, boolean isolateLegacyState) {
        if (!player.isOnline() || !worlds.isActive() || !plugin.getConfig().getBoolean("world.lock-to-experience", true)) return;
        World target = worlds.ensureWorld();
        Location destination = worlds.getEntranceLocation();
        if (target == null || destination == null) {
            plugin.getLogger().warning("Could not route " + player.getName() + ": the experience world is unavailable.");
            return;
        }
        if (isolateLegacyState && !player.getWorld().equals(target)) isolateLegacyInventory(player);
        if (!player.getWorld().equals(target)) {
            player.teleportAsync(destination).thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!success && player.isOnline()) player.sendMessage("§cCould not move you into Under The Bedrock. Please contact an administrator.");
                else if (success && player.isOnline()) applyExperienceState(player);
            }));
        } else {
            applyExperienceState(player);
        }
    }

    private void isolateLegacyInventory(Player player) {
        String id = player.getUniqueId().toString();
        if (isolated.getBoolean(id, false)) return;
        PlayerInventory inventory = player.getInventory();
        File backupFile = new File(plugin.getDataFolder(), "legacy-inventory-backups.yml");
        YamlConfiguration backup = YamlConfiguration.loadConfiguration(backupFile);
        backup.set(id + ".inventory", Arrays.asList(inventory.getContents()));
        backup.set(id + ".armor", Arrays.asList(inventory.getArmorContents()));
        backup.set(id + ".ender-chest", Arrays.asList(player.getEnderChest().getContents()));
        backup.set(id + ".captured-at", System.currentTimeMillis());
        try {
            backup.save(backupFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not safely isolate " + player.getName() + "'s legacy inventory: " + ex.getMessage());
            return;
        }
        inventory.clear();
        inventory.setArmorContents(null);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        isolated.set(id, true);
        saveIsolation();
    }

    private void applyExperienceState(Player player) {
        if (!player.isOnline() || !player.getWorld().equals(worlds.getWorld())) return;
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0);
        player.setFireTicks(0);
    }

    private void saveIsolation() {
        try { isolated.save(isolationFile); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save player isolation state: " + ex.getMessage()); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!worlds.isActive() || !plugin.getConfig().getBoolean("world.auto-join-on-connect", true)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> route(event.getPlayer(), true), 1L);
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
            if (anchor != null && anchor.getWorld() != null && anchor.getWorld().equals(target)) destination = anchor;
        }
        event.setRespawnLocation(destination);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!worlds.isActive() || !plugin.getConfig().getBoolean("world.lock-to-experience", true)) return;
        Location destination = worlds.getEntranceLocation();
        if (destination != null) event.setTo(destination);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!worlds.isActive() || !plugin.getConfig().getBoolean("world.lock-to-experience", true)) return;
        Player player = event.getPlayer();
        if (player.getWorld().equals(worlds.getWorld())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> route(player, false));
    }
}
