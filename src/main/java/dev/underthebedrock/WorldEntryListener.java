package dev.underthebedrock;

import dev.underthebedrock.item.CustomItemManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class WorldEntryListener implements Listener {
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final CustomItemManager customItems;
    private final File isolationFile;
    private final File backupFile;
    private final File positionFile;
    private final YamlConfiguration isolated;
    private final YamlConfiguration positions;
    private final Map<UUID, Location> lastSavedPositions = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask positionTask;

    public WorldEntryListener(UnderTheBedrockPlugin plugin, VoidWorldManager worlds, CustomItemManager customItems) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.customItems = customItems;
        isolationFile = new File(plugin.getDataFolder(), "player-isolation.yml");
        backupFile = new File(plugin.getDataFolder(), "legacy-inventory-backups.yml");
        positionFile = new File(plugin.getDataFolder(), "player-experience-positions.yml");
        isolated = YamlConfiguration.loadConfiguration(isolationFile);
        positions = YamlConfiguration.loadConfiguration(positionFile);
    }

    public void enableLockdown() {
        plugin.getConfig().set("world.lock-to-experience", true);
        plugin.saveConfig();
    }

    public void start() {
        if (positionTask != null) return;
        long interval = Math.max(40, plugin.getConfig().getLong("world.position-save-interval-ticks", 100));
        positionTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::savePlayerPositions, interval, interval);
    }

    public void stop() {
        if (positionTask != null) positionTask.cancel();
        positionTask = null;
        savePlayerPositions();
        for (Player player : Bukkit.getOnlinePlayers()) savePlayerPosition(player);
        savePositions();
    }

    public boolean enterExperience(Player player) {
        return route(player, true, false, true);
    }

    public void routeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> route(player, true, false));
        }
    }

    private boolean route(Player player, boolean isolateLegacyState) {
        return route(player, isolateLegacyState, false, false);
    }

    private boolean route(Player player, boolean isolateLegacyState, boolean resumeSavedPosition) {
        return route(player, isolateLegacyState, resumeSavedPosition, false);
    }

    private boolean route(Player player, boolean isolateLegacyState, boolean resumeSavedPosition, boolean forceEntry) {
        if (!player.isOnline() || !worlds.isActive()
                || (!forceEntry && !plugin.getConfig().getBoolean("world.lock-to-experience", true))) return false;

        World target = worlds.ensureWorld();
        if (target == null) {
            plugin.getLogger().warning("Could not route " + player.getName() + ": the experience world is unavailable.");
            return false;
        }
        Location destination = resumeSavedPosition ? savedPosition(player, target) : worlds.getEntranceLocation();
        if (destination == null) {
            plugin.getLogger().warning("Could not route " + player.getName() + ": the experience destination is unavailable.");
            return false;
        }

        if (isolateLegacyState && !isolateLegacyInventory(player)) {
            player.sendMessage(ChatColor.RED + "Your previous inventory could not be safely backed up. You have not been moved.");
            return false;
        }

        if (!player.getWorld().equals(target) || resumeSavedPosition) {
            player.teleportAsync(destination).thenAccept(success ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!success && player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Could not move you into Under The Bedrock. Please contact an administrator.");
                    } else if (player.isOnline()) {
                        applyExperienceState(player);
                    }
                })
            );
        } else {
            applyExperienceState(player);
        }
        return true;
    }

    /**
     * A durable PREPARED marker is written before the backup attempt. A crash before the
     * backup completes will retry capture on the next join; a crash after backup will reuse
     * the same immutable snapshot. A legacy backup with no matching isolation marker fails
     * closed rather than being overwritten or treated as safe to clear.
     */
    private boolean isolateLegacyInventory(Player player) {
        String id = player.getUniqueId().toString();
        // Backward compatibility: earlier releases stored a boolean isolation marker.
        if (isolated.getBoolean(id, false)) return true;
        String status = isolated.getString(id + ".status", "none");
        if ("complete".equals(status)) return true;

        if ("none".equals(status)) {
            if (backupExists(id)) {
                plugin.getLogger().severe("A legacy backup exists without a matching isolation marker for "
                    + player.getName() + "; refusing to clear inventory automatically.");
                return false;
            }
            isolated.set(id + ".status", "prepared");
            isolated.set(id + ".prepared-at", System.currentTimeMillis());
            isolated.set(id + ".protocol-version", 2);
            if (!saveAtomic(isolated, isolationFile)) {
                plugin.getLogger().severe("Could not mark inventory migration prepared for " + player.getName() + "; migration aborted.");
                return false;
            }
        }

        if (!backupExists(id)) {
            YamlConfiguration backup = YamlConfiguration.loadConfiguration(backupFile);
            PlayerInventory inventory = player.getInventory();
            backup.set(id + ".storage", Arrays.asList(inventory.getStorageContents()));
            backup.set(id + ".armor", Arrays.asList(inventory.getArmorContents()));
            backup.set(id + ".offhand", inventory.getItemInOffHand());
            backup.set(id + ".ender-chest", Arrays.asList(player.getEnderChest().getContents()));
            backup.set(id + ".level", player.getLevel());
            backup.set(id + ".exp", player.getExp());
            backup.set(id + ".total-experience", player.getTotalExperience());
            backup.set(id + ".game-mode", player.getGameMode().name());
            backup.set(id + ".allow-flight", player.getAllowFlight());
            backup.set(id + ".was-flying", player.isFlying());
            backup.set(id + ".health", player.getHealth());
            backup.set(id + ".food", player.getFoodLevel());
            backup.set(id + ".saturation", player.getSaturation());
            backup.set(id + ".potion-effects", player.getActivePotionEffects().stream()
                .map(PotionEffect::serialize).toList());
            backup.set(id + ".backup-protocol-version", 2);
            backup.set(id + ".captured-at", System.currentTimeMillis());
            if (!saveAtomic(backup, backupFile)) {
                plugin.getLogger().severe("Could not back up legacy inventory for " + player.getName() + "; migration aborted.");
                return false;
            }
        } else {
            YamlConfiguration backup = YamlConfiguration.loadConfiguration(backupFile);
            if (backup.getInt(id + ".backup-protocol-version", 0) != 2) {
                plugin.getLogger().severe("The saved backup format for " + player.getName()
                    + " is not a verified migration snapshot; refusing to clear inventory.");
                return false;
            }
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        isolated.set(id + ".status", "complete");
        isolated.set(id + ".completed-at", System.currentTimeMillis());
        if (!saveAtomic(isolated, isolationFile)) {
            plugin.getLogger().severe("Inventory migration for " + player.getName()
                + " is prepared but completion could not be saved. It will safely retry on next join.");
            return false;
        }
        return true;
    }

    private boolean backupExists(String id) {
        YamlConfiguration backup = YamlConfiguration.loadConfiguration(backupFile);
        return backup.contains(id + ".captured-at");
    }

    private boolean saveAtomic(YamlConfiguration configuration, File target) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            File temporary = new File(parent, target.getName() + ".tmp");
            configuration.save(temporary);
            try {
                Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save " + target.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private Location savedPosition(Player player, World target) {
        String id = player.getUniqueId().toString();
        String path = id;
        if (!target.getName().equals(positions.getString(path + ".world"))) return worlds.getEntranceLocation();
        double x = positions.getDouble(path + ".x", Double.NaN);
        double y = positions.getDouble(path + ".y", Double.NaN);
        double z = positions.getDouble(path + ".z", Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000) return worlds.getEntranceLocation();
        float yaw = (float) positions.getDouble(path + ".yaw", 0);
        float pitch = (float) positions.getDouble(path + ".pitch", 0);
        double safeY = Math.max(plugin.getConfig().getInt("world.floor-y", 64) + 1, y);
        if (safeY >= target.getMaxHeight()) safeY = plugin.getConfig().getInt("world.floor-y", 64) + 1;
        return new Location(target, x, safeY, z, yaw, pitch);
    }

    private void savePlayerPositions() {
        if (!worlds.isActive()) return;
        World target = worlds.getWorld();
        if (target == null) return;
        boolean dirty = false;
        for (Player player : target.getPlayers()) dirty |= savePlayerPosition(player);
        if (dirty) savePositions();
    }

    private boolean savePlayerPosition(Player player) {
        if (!player.isOnline() || !worlds.isActive() || !player.getWorld().equals(worlds.getWorld())) return false;
        Location location = player.getLocation();
        UUID id = player.getUniqueId();
        Location previous = lastSavedPositions.get(id);
        boolean movedEnough = previous == null || previous.getWorld() == null
                || !previous.getWorld().equals(location.getWorld())
                || previous.distanceSquared(location) >= 16.0
                || Math.abs(previous.getYaw() - location.getYaw()) >= 20.0f
                || Math.abs(previous.getPitch() - location.getPitch()) >= 20.0f;
        if (!movedEnough && positions.contains(id + ".saved-at")) return false;

        String path = id.toString();
        positions.set(path + ".world", location.getWorld().getName());
        positions.set(path + ".x", location.getX());
        positions.set(path + ".y", location.getY());
        positions.set(path + ".z", location.getZ());
        positions.set(path + ".yaw", location.getYaw());
        positions.set(path + ".pitch", location.getPitch());
        positions.set(path + ".saved-at", System.currentTimeMillis());
        lastSavedPositions.put(id, location.clone());
        return true;
    }

    private void savePositions() {
        if (!saveAtomic(positions, positionFile)) {
            plugin.getLogger().warning("Could not persist player experience positions; the last successful snapshot remains on disk.");
        }
    }

    private void applyExperienceState(Player player) {
        if (!player.isOnline() || !player.getWorld().equals(worlds.getWorld())) return;
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0);
        player.setFireTicks(0);
    }

    /**
     * Administrative shutdown only. Ordinary players still have no return command or normal
     * escape route; an operator stopping the experience evacuates occupants to the server spawn.
     */
    public boolean stopExperienceForAdmin() {
        World horror = worlds.getWorld();
        if (horror == null) return worlds.stop();

        World destination = Bukkit.getWorlds().stream().filter(world -> !world.equals(horror)).findFirst().orElse(null);
        List<Player> occupants = new ArrayList<>(horror.getPlayers());
        if (!occupants.isEmpty() && (!plugin.getConfig().getBoolean("world.admin-stop-evacuation", true) || destination == null)) return false;

        plugin.getConfig().set("world.lock-to-experience", false);
        plugin.saveConfig();

        List<Player> evacuated = new ArrayList<>();
        if (destination != null) {
            for (Player player : occupants) {
                if (!player.teleport(destination.getSpawnLocation())) {
                    plugin.getConfig().set("world.lock-to-experience", true);
                    plugin.saveConfig();
                    for (Player moved : evacuated) {
                        if (moved.isOnline()) moved.teleport(worlds.getEntranceLocation());
                    }
                    return false;
                }
                evacuated.add(player);
            }
        }

        if (worlds.stop()) return true;
        plugin.getConfig().set("world.lock-to-experience", true);
        plugin.saveConfig();
        for (Player moved : evacuated) {
            if (moved.isOnline()) moved.teleport(worlds.getEntranceLocation());
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!worlds.isActive()) return;
        boolean locked = plugin.getConfig().getBoolean("world.lock-to-experience", true);
        boolean autoJoin = plugin.getConfig().getBoolean("world.auto-join-on-connect", true);
        if (!locked && !autoJoin) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> route(event.getPlayer(), true, true, true), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (savePlayerPosition(event.getPlayer())) savePositions();
        lastSavedPositions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!(plugin.getConfig().getBoolean("world.auto-respawn", true)
                || plugin.getConfig().getBoolean("world.lock-to-experience", true)) || !worlds.isActive()) return;
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
        // Respawning from another world can bypass the join/world-change migration path.
        // Run the same durable inventory isolation after Bukkit completes the respawn.
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> route(player, true, false), 1L);
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
        if (player.getWorld().equals(worlds.getWorld())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> route(player, true, false));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> route(player, false, false));
    }
}
