package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** A cooldown-governed, per-player illusion and world-distortion director. */
public final class HorrorEventDirector implements Listener {
    private final UnderTheBedrockPlugin plugin;
    private final Random random = new Random();
    private final NamespacedKey districtSignKey;
    private final Map<UUID, Long> nextEvent = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> temporarilyHiddenPlayers = new HashMap<>();
    private BukkitTask task;

    public HorrorEventDirector(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        this.districtSignKey = new NamespacedKey(plugin, "civilization-sign");
    }

    public void start() {
        if (task != null) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        nextEvent.clear();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : new HashMap<>(temporarilyHiddenPlayers).entrySet()) {
            Player viewer = plugin.getServer().getPlayer(entry.getKey());
            if (viewer == null) continue;
            for (UUID targetId : entry.getValue().keySet()) {
                Player target = plugin.getServer().getPlayer(targetId);
                if (target != null) viewer.showPlayer(plugin, target);
            }
        }
        temporarilyHiddenPlayers.clear();
    }

    private void tick() {
        if (!plugin.getWorlds().isActive()) return;
        World world = plugin.getWorlds().getWorld();
        if (world == null) return;
        long now = System.currentTimeMillis();

        for (Player player : world.getPlayers()) {
            UUID id = player.getUniqueId();
            long due = nextEvent.computeIfAbsent(id, ignored -> now + 60_000L);
            if (now < due || random.nextDouble() > .22) continue;

            if (plugin.getFacilityManager() != null && plugin.getFacilityManager().isInsideSafeRoom(player.getLocation())) {
                nextEvent.put(id, now + 30_000L);
                continue;
            }

            PlayerHorrorMemory memory = plugin.getOccurrenceDirector().memory(id);
            fire(player, memory);
            long minimum = Math.max(30, plugin.getConfig().getLong("horror.events.minimum-cooldown-seconds", 150));
            long maximum = Math.max(minimum, plugin.getConfig().getLong("horror.events.maximum-cooldown-seconds", 360));
            long cooldown = minimum + (maximum == minimum ? 0 : random.nextLong(maximum - minimum + 1));
            nextEvent.put(id, now + cooldown * 1000L);
        }
        nextEvent.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    private void fire(Player player, PlayerHorrorMemory memory) {
        int stage = memory.discoveredClueCount() + memory.loreCount() / 2
            + (int) (memory.distanceTravelled() / 4096);
        int eventCount = stage >= 8 ? 14 : stage >= 3 ? 8 : 3;
        int event = selectEvent(eventCount, memory);
        memory.recordBehavior("event_" + event);
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0).normalize();
        if (direction.lengthSquared() < .01) direction = new Vector(0, 0, 1);
        Location distant = origin.clone().add(direction.clone()
            .rotateAroundY(Math.toRadians(random.nextBoolean() ? 90 : -90))
            .multiply(12 + random.nextInt(13)));
        distant.setY(origin.getY());

        switch (event) {
            case 0 -> {
                player.playSound(distant, Sound.BLOCK_STONE_STEP, .42f, .62f);
                if (random.nextBoolean()) player.playSound(distant.clone().add(0, 0, 1), Sound.BLOCK_STONE_STEP, .25f, .72f);
            }
            case 1 -> {
                player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_BASS, .2f, .45f);
                player.sendMessage("§8[LOCAL SIGNAL] §7A voice is waiting in the channel.");
            }
            case 2 -> {
                player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .18f, .5f);
                player.sendMessage("§8Your journal briefly displays an entry you have not written.");
            }
            case 3 -> {
                player.playSound(distant, Sound.BLOCK_IRON_DOOR_OPEN, .3f, .55f);
                player.sendMessage("§8Something opened a door behind the wall.");
            }
            case 4 -> {
                player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, .24f, .55f);
                player.sendMessage("§8[WORLD NOTICE] §7Identity mismatch. Please remain where you are.");
            }
            case 5 -> {
                player.playSound(distant, Sound.ENTITY_WARDEN_HEARTBEAT, .22f, .65f);
                player.sendMessage("§8You hear a second set of footsteps matching your pace.");
            }
            case 6 -> {
                player.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, .22f, .4f);
                player.sendMessage("§8[ARCHIVE SYNC] §7A resident record has been revised.");
            }
            case 7 -> fakeTeammateWarning(player, origin);
            case 8 -> fakeJoinLeave(player, origin);
            case 9 -> stagedDisappearance(player, origin, memory);
            case 10 -> clientSideBlackout(player, origin);
            case 11 -> clientSideRoomDistortion(player, origin);
            case 12 -> emergencyBroadcast(player, origin);
            default -> nullChangesItsAttention(player, memory);
        }

        memory.addTension(event >= 9 ? 2.2 : 1.0);
        plugin.getOccurrenceDirector().saveMemory(player.getUniqueId());
    }

    private int selectEvent(int count, PlayerHorrorMemory memory) {
        if (!plugin.getConfig().getBoolean("horror.events.event-variation-memory", true)) {
            return random.nextInt(count);
        }
        double total = 0;
        double[] weights = new double[count];
        for (int index = 0; index < count; index++) {
            weights[index] = 1.0 / (1.0 + Math.min(12, memory.behaviorCount("event_" + index)) * .28);
            total += weights[index];
        }
        double value = random.nextDouble() * total;
        for (int index = 0; index < count; index++) {
            value -= weights[index];
            if (value <= 0) return index;
        }
        return 0;
    }

    private void fakeTeammateWarning(Player player, Location origin) {
        Player nearby = nearestTeammate(player, origin);
        String voice = nearby == null ? "MARA" : nearby.getName();
        player.sendMessage("§8[LOCAL CHAT] §7" + voice + ": Don't turn around. I can see it.");
        player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, .2f, .5f);
        if (nearby != null) nearby.playSound(nearby.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .12f, .45f);
    }

    private void hideTemporarily(Player viewer, Player target, long durationTicks) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        long expiresAt = System.currentTimeMillis() + durationTicks * 50L;
        temporarilyHiddenPlayers.computeIfAbsent(viewerId, ignored -> new HashMap<>()).put(targetId, expiresAt);
        viewer.hidePlayer(plugin, target);
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> restoreTemporaryVisibility(viewerId, targetId), durationTicks);
    }

    private void restoreTemporaryVisibility(UUID viewerId, UUID targetId) {
        Map<UUID, Long> hidden = temporarilyHiddenPlayers.get(viewerId);
        if (hidden != null) {
            hidden.remove(targetId);
            if (hidden.isEmpty()) temporarilyHiddenPlayers.remove(viewerId);
        }
        Player viewer = plugin.getServer().getPlayer(viewerId);
        Player target = plugin.getServer().getPlayer(targetId);
        if (viewer != null && target != null) viewer.showPlayer(plugin, target);
        if (viewer != null && target != null && viewer.getWorld().equals(target.getWorld())) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .18f, .6f);
            viewer.sendMessage("§8" + target.getName() + " is standing where they were before.");
        }
    }

    private void refreshTemporaryVisibility(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        Map<UUID, Long> asViewer = temporarilyHiddenPlayers.get(playerId);
        if (asViewer != null) {
            for (Map.Entry<UUID, Long> entry : new HashMap<>(asViewer).entrySet()) {
                Player target = plugin.getServer().getPlayer(entry.getKey());
                if (entry.getValue() <= now) {
                    restoreTemporaryVisibility(playerId, entry.getKey());
                } else if (target != null) {
                    player.hidePlayer(plugin, target);
                }
            }
        }

        for (Map.Entry<UUID, Map<UUID, Long>> entry : new HashMap<>(temporarilyHiddenPlayers).entrySet()) {
            Long expiresAt = entry.getValue().get(playerId);
            if (expiresAt == null) continue;
            Player viewer = plugin.getServer().getPlayer(entry.getKey());
            if (viewer == null) continue;
            if (expiresAt <= now) {
                restoreTemporaryVisibility(entry.getKey(), playerId);
            } else {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshTemporaryVisibility(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshTemporaryVisibility(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        temporarilyHiddenPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void fakeJoinLeave(Player player, Location origin) {
        Player nearby = nearestTeammate(player, origin);
        String name = nearby == null ? "MARA" : nearby.getName();
        boolean joined = random.nextBoolean();
        player.sendMessage(joined
            ? "§7[Server: " + name + " joined the game]"
            : "§7[Server: " + name + " left the game]");
        player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_BASS, .18f, joined ? .7f : .45f);
        plugin.getOccurrenceDirector().recordBehavior(player, "saw_false_player_presence");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(joined
                    ? "§7[Server: " + name + " left the game]"
                    : "§7[Server: " + name + " joined the game]");
            }
        }, 45L);
    }

    private void stagedDisappearance(Player viewer, Location origin, PlayerHorrorMemory memory) {
        Player target = nearestTeammate(viewer, origin);
        if (target == null || !viewer.canSee(target)) {
            fakeJoinLeave(viewer, origin);
            return;
        }
        long duration = 60 + random.nextInt(61);
        hideTemporarily(viewer, target, duration);
        memory.recordBehavior("saw_teammate_disappear");
        plugin.getOccurrenceDirector().saveMemory(viewer.getUniqueId());
        viewer.sendMessage("§8" + target.getName() + " stopped responding.");
        viewer.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, .3f, .45f);
    }

    private void clientSideBlackout(Player player, Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        List<Location> changed = new ArrayList<>();
        for (int dx = -14; dx <= 14 && changed.size() < 12; dx++) {
            for (int dy = -3; dy <= 5 && changed.size() < 12; dy++) {
                for (int dz = -14; dz <= 14 && changed.size() < 12; dz++) {
                    int x = origin.getBlockX() + dx, y = origin.getBlockY() + dy, z = origin.getBlockZ() + dz;
                    if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.REDSTONE_LAMP || !(block.getBlockData() instanceof Lightable light) || !light.isLit()) continue;
                    BlockData off = block.getBlockData().clone();
                    ((Lightable) off).setLit(false);
                    player.sendBlockChange(block.getLocation(), off);
                    changed.add(block.getLocation().clone());
                }
            }
        }
        player.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, .32f, .42f);
        player.sendMessage("§8The power has failed. You cannot hear the backup generator.");
        restoreClientBlocks(player, changed, 100L);
    }

    private void clientSideRoomDistortion(Player player, Location origin) {
        World world = origin.getWorld();
        if (world == null) return;

        Location structure = findNearbyStructure(origin);
        Location center = structure != null ? structure : origin;
        if (center.distanceSquared(origin) > 36 * 36) center = origin;

        List<Location> changed = new ArrayList<>();
        Material[] substitutions = {
            Material.CRYING_OBSIDIAN, Material.POLISHED_BLACKSTONE_BRICKS,
            Material.DEEPSLATE_TILES, Material.AIR
        };
        int radius = structure == null ? 8 : 18;
        int maximumChanges = structure == null ? 24 : 100;
        int minY = structure == null ? -1 : -3;
        int maxY = structure == null ? 4 : 7;

        for (int dx = -radius; dx <= radius && changed.size() < maximumChanges; dx++) {
            for (int dy = minY; dy <= maxY && changed.size() < maximumChanges; dy++) {
                for (int dz = -radius; dz <= radius && changed.size() < maximumChanges; dz++) {
                    int x = center.getBlockX() + dx, y = center.getBlockY() + dy, z = center.getBlockZ() + dz;
                    if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (!isDistortible(block.getType())) continue;
                    if (block.getState() instanceof org.bukkit.block.TileState) continue;
                    changed.add(block.getLocation().clone());
                    player.sendBlockChange(block.getLocation(), substitutions[random.nextInt(substitutions.length)].createBlockData());
                }
            }
        }
        player.sendTitle("§8", structure == null
            ? "§7The room is not arranged like this."
            : "§7The building has changed while you were inside.", 5, 35, 10);
        player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .22f, .42f);
        restoreClientBlocks(player, changed, 90L);
    }

    private Location findNearbyStructure(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        Location nearest = null;
        double nearestDistance = 28 * 28;
        for (int dx = -20; dx <= 20; dx++) {
            for (int dy = -3; dy <= 5; dy++) {
                for (int dz = -20; dz <= 20; dz++) {
                    int x = origin.getBlockX() + dx, y = origin.getBlockY() + dy, z = origin.getBlockZ() + dz;
                    if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (!(block.getState() instanceof Sign sign)
                            || !sign.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)) continue;
                    double distance = block.getLocation().distanceSquared(origin);
                    if (distance < nearestDistance) {
                        nearest = block.getLocation();
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean isDistortible(Material material) {
        return material == Material.STONE_BRICKS || material == Material.CRACKED_STONE_BRICKS
            || material == Material.DEEPSLATE_BRICKS || material == Material.DEEPSLATE_TILES
            || material == Material.POLISHED_ANDESITE || material == Material.POLISHED_BLACKSTONE_BRICKS
            || material == Material.BRICKS || material == Material.OAK_PLANKS
            || material == Material.SPRUCE_PLANKS || material == Material.GLASS
            || material == Material.IRON_BARS;
    }

    private void restoreClientBlocks(Player player, List<Location> locations, long delayTicks) {
        World expectedWorld = locations.isEmpty() ? null : locations.getFirst().getWorld();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || (expectedWorld != null && !player.getWorld().equals(expectedWorld))) return;
            for (Location location : locations) {
                World world = location.getWorld();
                if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
                player.sendBlockChange(location, world.getBlockAt(location).getBlockData());
            }
        }, delayTicks);
    }

    private void emergencyBroadcast(Player player, Location origin) {
        player.playSound(origin, Sound.BLOCK_BELL_USE, .28f, .6f);
        player.sendTitle("§4EMERGENCY BROADCAST", "§7Remain inside the marked boundary.", 5, 55, 15);
        player.sendMessage("§8[EMERGENCY CHANNEL 04] §7CIVILIAN TRANSFER CANCELLED. DO NOT APPROACH THE STATION.");
        plugin.getOccurrenceDirector().recordBehavior(player, "heard_emergency_broadcast");
    }

    private void nullChangesItsAttention(Player player, PlayerHorrorMemory memory) {
        memory.provoke(4.0);
        memory.addTension(3.0);
        memory.recordBehavior("null_escalated_event");
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, .42f, .48f);
        player.sendTitle("§8SIGNAL DETECTED", "§7It has changed what it is looking for.", 5, 50, 15);
        plugin.getOccurrenceDirector().saveMemory(player.getUniqueId());
    }

    private Player nearestTeammate(Player player, Location origin) {
        return player.getWorld().getPlayers().stream()
            .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
            .filter(other -> other.getLocation().distanceSquared(origin) <= 48 * 48)
            .min(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(origin)))
            .orElse(null);
    }
}
