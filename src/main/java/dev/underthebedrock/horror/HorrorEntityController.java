package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.integration.BetterModelIntegration;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Player-specific apparition AI. Encounter selection is slow and rarity-gated; movement and
 * observation update independently so entities move smoothly without force-loading chunks.
 */
public final class HorrorEntityController implements Listener {
    private enum State { OBSERVING, STALKING, SEARCHING, HUNTING, RETREATING, DISAPPEARING }

    private static final class Encounter {
        private final UUID target;
        private final EntityArchetype type;
        private final ArmorStand shell;
        private final long startedAt;
        private Location lastKnown;
        private State state = State.OBSERVING;
        private int unseenChecks;
        private int observedChecks;

        private Encounter(UUID target, EntityArchetype type, ArmorStand shell, Location lastKnown) {
            this.target = target;
            this.type = type;
            this.shell = shell;
            this.lastKnown = lastKnown.clone();
            startedAt = System.currentTimeMillis();
        }
    }

    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final OccurrenceDirector director;
    private final BetterModelIntegration models;
    private final Map<UUID, Encounter> active = new HashMap<>();
    private final Map<UUID, Long> nextAttemptTick = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;
    private long tickCounter;
    private boolean warned;

    public HorrorEntityController(UnderTheBedrockPlugin plugin, VoidWorldManager worlds,
                                  OccurrenceDirector director, BetterModelIntegration models) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.director = director;
        this.models = models;
    }

    public void start() {
        if (task != null) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long interval = Math.max(1, plugin.getConfig().getLong("horror.entities.behavior.movement-tick-interval", 2));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        clearAll();
        nextAttemptTick.clear();
    }

    private void tick() {
        long interval = Math.max(1, plugin.getConfig().getLong("horror.entities.behavior.movement-tick-interval", 2));
        tickCounter += interval;
        if (!worlds.isActive()) { clearAll(); return; }
        World world = worlds.getWorld();
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            Encounter encounter = active.get(player.getUniqueId());
            if (plugin.getFacilityManager() != null && plugin.getFacilityManager().isInsideSafeRoom(player.getLocation())) {
                if (encounter != null) {
                    remove(encounter);
                    active.remove(player.getUniqueId());
                }
                continue;
            }
            if (encounter == null) {
                long due = nextAttemptTick.getOrDefault(player.getUniqueId(), tickCounter);
                if (tickCounter >= due) {
                    long attemptInterval = Math.max(20, plugin.getConfig().getLong("horror.entities.behavior.encounter-check-interval-ticks", 60));
                    nextAttemptTick.put(player.getUniqueId(), tickCounter + attemptInterval);
                    attempt(player);
                }
            } else {
                update(player, encounter);
            }
        }

        active.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.getWorld().equals(world)) return false;
            remove(entry.getValue());
            return true;
        });
        nextAttemptTick.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    private void attempt(Player player) {
        if (models.availableModels().isEmpty()) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("No BetterModel models registered; entity encounters paused.");
            }
            return;
        }
        Location spawn = findSpawn(player);
        if (spawn == null) return;
        EntityArchetype type = director.trySelectEntityEncounter(player).orElse(null);
        if (type == null) return;

        String key = type.name().toLowerCase(java.util.Locale.ROOT);
        String model = plugin.getConfig().getString("horror.entities.models." + key,
            plugin.getConfig().getString("integrations.bettermodel.default-watcher-model", "watcher"));
        if (!models.availableModels().contains(model)) {
            String fallback = plugin.getConfig().getString("integrations.bettermodel.default-watcher-model", "watcher");
            if (!models.availableModels().contains(fallback)) {
                plugin.getLogger().warning("No registered BetterModel model for " + type + " or fallback '" + fallback + "'.");
                return;
            }
            model = fallback;
        }

        ArmorStand shell = player.getWorld().spawn(spawn, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setCustomNameVisible(false);
        });
        if (models.attachModel(shell, model).isEmpty()) {
            shell.remove();
            plugin.getLogger().warning("Could not attach BetterModel '" + model + "' for " + type + ".");
            return;
        }

        Encounter encounter = new Encounter(player.getUniqueId(), type, shell, player.getLocation());
        active.put(player.getUniqueId(), encounter);
        applyPrivacyToViewers(encounter);
        director.recordEntityEncounter(player, type);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, .12f, .55f);
    }

    private Location findSpawn(Player player) {
        Location origin = player.getLocation();
        Vector forward = origin.getDirection().setY(0).normalize();
        if (forward.lengthSquared() < .01) forward = new Vector(0, 0, 1);
        int floor = plugin.getConfig().getInt("world.floor-y", 64);
        double minimum = Math.max(32, plugin.getConfig().getDouble("horror.entities.behavior.minimum-spawn-distance-blocks", 64));
        double maximum = Math.max(minimum + 8, plugin.getConfig().getDouble("horror.entities.behavior.maximum-spawn-distance-blocks", 112));

        for (int i = 0; i < 20; i++) {
            double angle = Math.toRadians(105 + random.nextDouble() * 150);
            Vector offset = forward.clone().rotateAroundY(angle).multiply(minimum + random.nextDouble() * (maximum - minimum));
            int x = origin.getBlockX() + offset.getBlockX();
            int z = origin.getBlockZ() + offset.getBlockZ();
            if (!player.getWorld().isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            Location location = new Location(player.getWorld(), x + .5, floor + 1, z + .5);
            if (location.distanceSquared(origin) < minimum * minimum) continue;
            if (location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable()) return location;
        }
        return null;
    }

    private void update(Player player, Encounter encounter) {
        ArmorStand shell = encounter.shell;
        if (!shell.isValid() || !shell.getWorld().equals(player.getWorld())) {
            remove(encounter);
            active.remove(player.getUniqueId());
            return;
        }
        Location here = shell.getLocation();
        double distanceSquared = here.distanceSquared(player.getLocation());
        boolean inViewRange = distanceSquared < Math.pow(plugin.getConfig().getDouble(
            "horror.entities.behavior.observation-range-blocks", 144), 2);
        boolean visible = inViewRange && player.hasLineOfSight(shell);

        if (visible) {
            encounter.lastKnown = player.getLocation().clone();
            encounter.observedChecks++;
            encounter.unseenChecks = 0;
            encounter.state = State.OBSERVING;
        } else {
            encounter.unseenChecks++;
        }

        long maxDuration = Math.max(15, plugin.getConfig().getLong("horror.entities.max-encounter-duration-seconds", 180)) * 1000L;
        double despawnRange = Math.max(32, plugin.getConfig().getDouble("horror.entities.behavior.despawn-range-blocks", 208));
        if (distanceSquared > despawnRange * despawnRange || System.currentTimeMillis() - encounter.startedAt > maxDuration) {
            encounter.state = State.DISAPPEARING;
        }

        switch (encounter.type) {
            case WITNESS, OBSERVER, FORGOTTEN_RESIDENT, SURVEYOR, UNPERSON, ORIGINAL -> {
                if (visible && distanceSquared < 3600) encounter.state = State.DISAPPEARING;
            }
            case THE_WATCHER -> {
                // Holds its position and only vanishes when the player closes in.
                if (visible && distanceSquared < 2304) encounter.state = State.DISAPPEARING;
            }
            case PEEKER, DOORFRAME, LURKER, CEILING_CRAWLER -> {
                if (visible) encounter.state = State.RETREATING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case PURSUER, AGGRESSOR, CRAWLING_ONE, HEAVY, EXCAVATOR -> {
                double provocation = director.memory(player.getUniqueId()).provocation();
                if (provocation >= 12 || distanceSquared < 1600)
                    encounter.state = visible ? State.SEARCHING : State.HUNTING;
                else if (visible) encounter.state = State.OBSERVING;
            }
            case THE_CRAWLER -> {
                double provocation = director.memory(player.getUniqueId()).provocation();
                if (visible) encounter.state = State.RETREATING;
                else if (provocation >= 6 || distanceSquared < 6400) encounter.state = State.HUNTING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case MIMIC, IMITATOR, DOUBLE -> {
                if (visible) encounter.state = State.OBSERVING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.SEARCHING;
            }
            case THE_MIMIC -> {
                if (!visible && encounter.unseenChecks > 0 && encounter.unseenChecks % 10 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_STEP, .18f,
                        .72f + random.nextFloat() * .28f);
                }
                if (visible) encounter.state = State.OBSERVING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.SEARCHING;
            }
            case ECHO -> {
                if (!visible && encounter.unseenChecks % 12 == 0)
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_STEP, .16f, .65f);
                if (visible && distanceSquared < 2500) encounter.state = State.DISAPPEARING;
            }
            case MISPRINT, ABSENCE -> {
                if (visible && encounter.observedChecks >= 2) encounter.state = State.DISAPPEARING;
            }
            case WANDERER, DEEP_ENTITY -> {
                if (!visible && random.nextDouble() < .012) encounter.state = State.STALKING;
            }
            case THE_DRIFTER -> {
                if (visible) encounter.state = State.OBSERVING;
                else if (encounter.unseenChecks >= 3) encounter.state = State.STALKING;
            }
            case STALKER -> encounter.state = visible ? State.OBSERVING : State.STALKING;
        }

        if (encounter.state == State.DISAPPEARING) {
            remove(encounter);
            active.remove(player.getUniqueId());
            return;
        }

        double step = Math.max(.04, plugin.getConfig().getDouble("horror.entities.behavior.movement-step-blocks", .20));
        if (encounter.state == State.RETREATING) {
            moveAway(shell, player.getLocation(), step * 1.8);
            if (distanceSquared > 10000) { remove(encounter); active.remove(player.getUniqueId()); }
            return;
        }
        if (encounter.state == State.STALKING) {
            if (encounter.type == EntityArchetype.DOORFRAME && encounter.unseenChecks == 2) {
                Location behind = player.getLocation().subtract(player.getLocation().getDirection().setY(0).normalize().multiply(12));
                behind.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
                teleportIfLoaded(shell, behind);
            } else {
                double stalkSpeed = encounter.type == EntityArchetype.CEILING_CRAWLER ? step * 1.8 : step * .85;
                moveToward(shell, encounter.lastKnown, stalkSpeed);
            }
            return;
        }
        if (encounter.state == State.HUNTING) {
            double speed = encounter.type == EntityArchetype.HEAVY ? step * .8
                : encounter.type == EntityArchetype.CRAWLING_ONE ? step * 2.5
                : encounter.type == EntityArchetype.THE_CRAWLER ? step * 1.45 : step * 1.9;
            moveToward(shell, encounter.lastKnown, speed);
            return;
        }
        if (encounter.state == State.SEARCHING && !visible) {
            moveToward(shell, encounter.lastKnown, step * 1.1);
            return;
        }
        if ((encounter.type == EntityArchetype.WANDERER || encounter.type == EntityArchetype.THE_DRIFTER)
                && random.nextDouble() < (encounter.type == EntityArchetype.THE_DRIFTER ? .003 : .002)) {
            Location roam = shell.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            roam.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
            teleportIfLoaded(shell, roam);
        }
    }

    private void moveToward(ArmorStand shell, Location target, double maximumStep) {
        if (target == null || !target.getWorld().equals(shell.getWorld())) return;
        Vector direction = target.toVector().subtract(shell.getLocation().toVector());
        direction.setY(0);
        double distance = direction.length();
        if (distance < .1) return;
        direction.normalize().multiply(Math.min(maximumStep, distance));
        Location next = shell.getLocation().add(direction);
        next.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
        teleportIfLoaded(shell, next);
    }

    private void moveAway(ArmorStand shell, Location target, double step) {
        Vector direction = shell.getLocation().toVector().subtract(target.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < .01) direction = new Vector(1, 0, 0);
        direction.normalize().multiply(step);
        Location next = shell.getLocation().add(direction);
        next.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
        teleportIfLoaded(shell, next);
    }

    private void teleportIfLoaded(ArmorStand shell, Location destination) {
        if (destination.getWorld() == null) return;
        int chunkX = Math.floorDiv(destination.getBlockX(), 16), chunkZ = Math.floorDiv(destination.getBlockZ(), 16);
        if (!destination.getWorld().isChunkLoaded(chunkX, chunkZ)) return;
        Block feet = destination.getBlock();
        Block head = destination.clone().add(0, 1, 0).getBlock();
        if (feet.isPassable() && head.isPassable()) shell.teleport(destination);
    }

    private void applyPrivacyToViewers(Encounter encounter) {
        if (!encounter.shell.isValid()) return;
        for (Player viewer : encounter.shell.getWorld().getPlayers()) {
            if (!viewer.getUniqueId().equals(encounter.target)) viewer.hideEntity(plugin, encounter.shell);
        }
    }

    private void applyPrivacyToNewViewer(Player viewer) {
        for (Encounter encounter : active.values()) {
            if (encounter.shell.isValid() && encounter.shell.getWorld().equals(viewer.getWorld())
                    && !encounter.target.equals(viewer.getUniqueId())) {
                viewer.hideEntity(plugin, encounter.shell);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyPrivacyToNewViewer(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (worlds.isActive() && event.getPlayer().getWorld().equals(worlds.getWorld())) {
            applyPrivacyToNewViewer(event.getPlayer());
        }
    }

    private void remove(Encounter encounter) {
        if (encounter.shell.isValid()) encounter.shell.remove();
    }

    private void clearAll() {
        for (Encounter encounter : active.values()) remove(encounter);
        active.clear();
    }
}
