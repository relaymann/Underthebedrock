package dev.underthebedrock.world;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.integration.BetterModelIntegration;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Permanent, deterministic progression landmark. Only its perimeter segments that are naturally
 * loaded are built; the three compact maze rooms are built once when the northern approach loads.
 * Player trial state is private and persistent; the physical route is shared and never regenerates.
 */
public final class NullEnclosureManager implements Listener {
    private static final int RADIUS = 1500;
    private static final int WALL_HALF_THICKNESS = 8;
    private static final int HEIGHT = 18;
    private static final int FLOOR = 64;
    private static final int[] ROOM_CENTRES = {-1450, -1505, -1570};
    private static final int ROOM_HALF = 16;
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final BetterModelIntegration models;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Trial> trials = new HashMap<>();
    private final Map<UUID, Apostle> apostles = new HashMap<>();
    private final Map<UUID, Apostle> returnWitnesses = new HashMap<>();
    private final Set<String> builtChunks = new HashSet<>();
    private final NamespacedKey protectedKey;
    private final NamespacedKey trialDeathKey;
    private BukkitTask task;
    private boolean mazesBuilt;
    private long lastPerimeterSaveAt;

    private static final class Trial {
        int stage;
        boolean unlocked;
        boolean switchActivated;
        Trial(int stage, boolean unlocked, boolean switchActivated) {
            this.stage = stage; this.unlocked = unlocked; this.switchActivated = switchActivated;
        }
    }
    private static final class Apostle {
        final UUID target;
        final ArmorStand shell;
        final EntityTracker tracker;
        final boolean hunter;
        Location lastKnown;
        long lastSeenAt;
        long nextTurnAt;
        boolean watching;
        final long createdAt = System.currentTimeMillis();
        long lastFootstepAt;
        String activeAnimation = "";
        String stationarySpot = "";
        String lastRecordedHideSpot = "";
        long stationarySince;
        final Map<String, Integer> hideSpotVisits = new HashMap<>();
        Apostle(UUID target, ArmorStand shell, EntityTracker tracker, boolean hunter, Location lastKnown) {
            this.target = target; this.shell = shell; this.tracker = tracker; this.hunter = hunter;
            this.lastKnown = lastKnown.clone(); this.lastSeenAt = System.currentTimeMillis();
        }
        void remove() { if (tracker != null && !tracker.isClosed()) tracker.close(); if (shell.isValid()) shell.remove(); }
    }

    public NullEnclosureManager(UnderTheBedrockPlugin plugin, VoidWorldManager worlds, BetterModelIntegration models) {
        this.plugin = plugin; this.worlds = worlds; this.models = models;
        file = new File(plugin.getDataFolder(), "null-enclosure.yml");
        data = YamlConfiguration.loadConfiguration(file);
        protectedKey = new NamespacedKey(plugin, "null-enclosure-block");
        trialDeathKey = new NamespacedKey(plugin, "null-enclosure-trial-death");
        for (String key : data.getStringList("built-perimeter-chunks")) builtChunks.add(key);
        for (String id : data.getKeys(false)) {
            if (id.equals("built-perimeter-chunks") || id.equals("mazes-built")) continue;
            try {
                UUID uuid = UUID.fromString(id);
                trials.put(uuid, new Trial(data.getInt(id + ".stage"), data.getBoolean(id + ".unlocked"),
                        data.getBoolean(id + ".switch-activated")));
            } catch (IllegalArgumentException ignored) { }
        }
        mazesBuilt = data.getBoolean("mazes-built", false);
    }

    public void start() {
        if (task != null || !plugin.getConfig().getBoolean("horror.enclosure.enabled", true)) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Apostle apostle : apostles.values()) apostle.remove();
        for (Apostle apostle : returnWitnesses.values()) apostle.remove();
        apostles.clear(); returnWitnesses.clear();
        save();
    }

    private Trial trial(Player player) {
        return trials.computeIfAbsent(player.getUniqueId(), ignored -> new Trial(0, false, false));
    }
    public boolean hasEscaped(UUID player) { if (!plugin.getConfig().getBoolean("horror.enclosure.enabled", true)) return true; Trial t = trials.get(player); return t != null && t.unlocked; }

    private Location centre(World world, int z) { return new Location(world, world.getSpawnLocation().getX(), FLOOR + 1, z); }
    private void tick() {
        if (!worlds.isActive()) return;
        World world = worlds.getWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            double distance = Math.hypot(player.getLocation().getX() - world.getSpawnLocation().getX(),
                    player.getLocation().getZ() - world.getSpawnLocation().getZ());
            if (distance >= 1320 && distance <= 1740 && player.getLocation().getZ() < -1250
                    && !mazesBuilt) buildMazes(world);
        }
        for (Apostle apostle : new ArrayList<>(apostles.values())) updateApostle(apostle);
    }

    /** Builds only the ring slice represented by an already-loaded chunk; never force-loads. */
    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!worlds.isActive() || event.getWorld() != worlds.getWorld()) return;
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        String key = cx + ":" + cz;
        if (builtChunks.contains(key)) return;
        int floor = plugin.getConfig().getInt("world.floor-y", FLOOR);
        int spawnX = worlds.getWorld().getSpawnLocation().getBlockX();
        int spawnZ = worlds.getWorld().getSpawnLocation().getBlockZ();
        boolean touchedRing = false;
        for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
            double r = Math.hypot(x - spawnX, z - spawnZ);
            int configuredRadius = plugin.getConfig().getInt("horror.enclosure.radius-blocks", RADIUS);
            int configuredThickness = Math.max(4, plugin.getConfig().getInt("horror.enclosure.wall-thickness-blocks", WALL_HALF_THICKNESS * 2)) / 2;
            if (Math.abs(r - configuredRadius) > configuredThickness) continue;
            // The north gate is deliberately open; the maze itself occupies this crossing.
            if (x >= spawnX - 20 && x <= spawnX + 20 && z < spawnZ) continue;
            touchedRing = true;
            for (int y = floor + 1; y <= floor + plugin.getConfig().getInt("horror.enclosure.wall-height-blocks", HEIGHT); y++) {
                Block block = event.getWorld().getBlockAt(x, y, z);
                if (block.getType().isAir()) {
                    block.setType(y <= floor + 3 ? Material.DEEPSLATE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS, false);
                    block.getChunk().getPersistentDataContainer().set(protectedKey, PersistentDataType.BYTE, (byte) 1);

                }
            }
            for (int y = floor - 3; y <= floor; y++) {
                Block foundation = event.getWorld().getBlockAt(x, y, z);
                if (foundation.getType().isAir()) foundation.setType(Material.BEDROCK, false);
            }
        }
        if (touchedRing) {
            builtChunks.add(key);
            long now = System.currentTimeMillis();
            if (now - lastPerimeterSaveAt >= 30_000L) {
                save();
                lastPerimeterSaveAt = now;
            }
        }
    }

    private void buildMazes(World world) {
        int floor = plugin.getConfig().getInt("world.floor-y", FLOOR);
        int sx = world.getSpawnLocation().getBlockX();
        int sz = world.getSpawnLocation().getBlockZ();
        for (int i = 0; i < 3; i++) {
            char[][] layout = Maze.generate(7 + (i == 2 ? 1 : 0), 0x4E554C4CL + i * 7919L, i == 0 ? 1 : i == 1 ? 3 : 5);
            int size = layout.length;
            int width = size * 2;
            int left = sx - width / 2;
            int north = sz + ROOM_CENTRES[i] - width / 2;
            for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
                int row = z / 2, col = x / 2;
                boolean wall = layout[row][col] == '#';
                int wx = left + x, wz = north + z;
                Block floorBlock = world.getBlockAt(wx, floor, wz);
                if (floorBlock.getType() == Material.BEDROCK) {
                    // Preserve the void world's bedrock floor; the route is built above it.
                }
                for (int y = floor + 1; y <= floor + 4; y++) {
                    Block block = world.getBlockAt(wx, y, wz);
                    if (wall) block.setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                    else if (y == floor + 1) block.setType(Material.AIR, false);
                }
                if (wall) {
                    Block top = world.getBlockAt(wx, floor + 5, wz);
                    top.setType(Material.CRYING_OBSIDIAN, false);
                }
            }
            // Open a generous, consistent entrance and exit; players can pass in either direction.
            for (int x = -2; x <= 2; x++) for (int y = floor + 1; y <= floor + 3; y++) {
                world.getBlockAt(sx + x, y, north + width - 1).setType(Material.AIR, false);
                world.getBlockAt(sx + x, y, north).setType(Material.AIR, false);
            }
            addMarkings(world, sx, floor, north, width, i);
        }
        // A short sprint corridor joins the second maze to the third. Obstacles are fixed and fair.
        for (int z = sz - 1530; z >= sz - 1550; z--) {
            for (int x = sx - 14; x <= sx - 6; x++) for (int y = floor + 1; y <= floor + 4; y++)
                world.getBlockAt(x, y, z).setType(Material.DEEPSLATE_BRICKS, false);
            for (int x = sx + 6; x <= sx + 14; x++) for (int y = floor + 1; y <= floor + 4; y++)
                world.getBlockAt(x, y, z).setType(Material.DEEPSLATE_BRICKS, false);
            world.getBlockAt(sx - 5, floor + 1, z).setType(Material.DEEPSLATE_BRICKS, false);
            world.getBlockAt(sx - 5, floor + 2, z).setType(Material.DEEPSLATE_BRICKS, false);
            world.getBlockAt(sx + 5, floor + 1, z).setType(Material.DEEPSLATE_BRICKS, false);
            world.getBlockAt(sx + 5, floor + 2, z).setType(Material.DEEPSLATE_BRICKS, false);
            if (z == sz - 1536 || z == sz - 1543) {
                int obstacleX = z == sz - 1536 ? sx - 2 : sx + 2;
                world.getBlockAt(obstacleX, floor + 1, z).setType(Material.CRYING_OBSIDIAN, false);
                world.getBlockAt(obstacleX, floor + 2, z).setType(Material.CRYING_OBSIDIAN, false);
            }
        }
        // Close the unused sides of the ring crossing so the maze is the only walk-through.
        for (int z = sz - 1428; z >= sz - 1610; z--) for (int y = floor + 1; y <= floor + 7; y++) {
            world.getBlockAt(sx - 19, y, z).setType(Material.DEEPSLATE_BRICKS, false);
            world.getBlockAt(sx + 19, y, z).setType(Material.DEEPSLATE_BRICKS, false);
            for (int x = sx - 18; x <= sx - 16; x++)
                world.getBlockAt(x, y, z).setType(Material.DEEPSLATE_BRICKS, false);
            for (int x = sx + 15; x <= sx + 17; x++)
                world.getBlockAt(x, y, z).setType(Material.DEEPSLATE_BRICKS, false);
        }
        // Observation rooms contain empty seats and monitors showing corridor feeds.
        buildObservationRoom(world, sx - 29, floor, sz - 1505, false);
        buildObservationRoom(world, sx + 29, floor, sz - 1570, true);
        // Traces of failed attempts: discarded equipment, a snag of webbing, and a warning.
        world.getBlockAt(sx - 8, floor + 1, sz - 1518).setType(Material.COBWEB, false);
        world.getBlockAt(sx - 7, floor + 1, sz - 1508).setType(Material.CAMPFIRE, false);
        world.getBlockAt(sx + 7, floor + 1, sz - 1519).setType(Material.TRIPWIRE_HOOK, false);
        sign(world, sx + 7, floor + 1, sz - 1508, "I MADE IT BACK");
        // A featureless silhouette is visible around a Maze Two corner before the chase.
        for (int y = floor + 1; y <= floor + 4; y++)
            world.getBlockAt(sx - 12, y, sz - 1514).setType(Material.BLACK_CONCRETE, false);
        // Maze Three's power switch and final door mechanism.
        Block switchBlock = world.getBlockAt(sx + 12, floor + 1, sz - 1570);
        switchBlock.setType(Material.LEVER, false);
        world.getBlockAt(sx + 12, floor + 2, sz - 1570).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        sign(world, sx + 9, floor + 1, sz - 1570, "MARKINGS: III / I / II");
        sign(world, sx - 8, floor + 1, sz - 1570, "THE POWER IS NOT THE EXIT");
        for (int x = -15; x <= 14; x++) for (int y = floor + 1; y <= floor + 3; y++)
            world.getBlockAt(sx + x, y, sz - 1600).setType(Material.IRON_BLOCK, false);
        sign(world, sx, floor + 1, sz - 1597, "YOU ARE NOT OUT");
        data.set("mazes-built", true); mazesBuilt = true; save();
        plugin.getLogger().info("The Null Enclosure's permanent maze route has been generated.");
    }

    private void buildObservationRoom(World world, int cx, int floor, int cz, boolean reverse) {
        for (int x = -5; x <= 5; x++) for (int z = -4; z <= 4; z++) {
            boolean edge = Math.abs(x) == 5 || Math.abs(z) == 4;
            for (int y = floor + 1; y <= floor + 4; y++) {
                Block block = world.getBlockAt(cx + x, y, cz + z);
                if (edge && ((reverse && x == -5 && z == 0) || (!reverse && x == 5 && z == 0)) && y <= floor + 3) block.setType(Material.AIR, false);
                else if (edge && y != floor + 2 && y != floor + 3) block.setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                else if (edge) block.setType(Material.TINTED_GLASS, false);
                else if (y == floor + 1) block.setType(Material.AIR, false);
            }
        }
        world.getBlockAt(cx - 2, floor + 1, cz + 1).setType(Material.DEEPSLATE_BRICK_STAIRS, false);
        world.getBlockAt(cx + 2, floor + 1, cz + 1).setType(Material.DEEPSLATE_BRICK_STAIRS, false);
        world.getBlockAt(cx, floor + 1, cz - 1).setType(Material.BLACK_CONCRETE, false);
        world.getBlockAt(cx, floor + 2, cz - 1).setType(Material.REDSTONE_LAMP, false);
        world.getBlockAt(cx, floor + 3, cz - 1).setType(Material.REDSTONE_LAMP, false);
        sign(world, cx, floor + 1, cz + 3, reverse ? "FEED 03 / LIVE" : "OBSERVATION 04");
        sign(world, cx, floor + 1, cz - 3, "NO ONE IS SITTING THERE");
    }

    private void addMarkings(World world, int sx, int floor, int north, int width, int index) {
        int z = north + width / 2;
        sign(world, sx - 10, floor + 1, z, index == 0 ? "KEEP WALKING" : index == 1 ? "DO NOT FOLLOW" : "III / I / II");
        if (index == 1) sign(world, sx + 10, floor + 1, z, "IT IS BEHIND YOU");
    }
    private void sign(World world, int x, int y, int z, String text) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.OAK_WALL_SIGN, false);
        if (b.getState() instanceof org.bukkit.block.Sign sign) {
            String remaining = text;
            for (int line = 0; line < 4 && !remaining.isEmpty(); line++) {
                int end = Math.min(15, remaining.length());
                if (end < remaining.length() && remaining.lastIndexOf(' ', end) > 0) end = remaining.lastIndexOf(' ', end);
                sign.setLine(line, "§8" + remaining.substring(0, end).trim());
                remaining = remaining.substring(end).trim();
            }
            sign.update(true, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        if (event.getTo() == null || event.getFrom().getWorld() != worlds.getWorld()) return;
        Player player = event.getPlayer();
        Trial t = trial(player);
        int z = event.getTo().getBlockZ() - worlds.getWorld().getSpawnLocation().getBlockZ();
        int x = Math.abs(event.getTo().getBlockX() - worlds.getWorld().getSpawnLocation().getBlockX());
        if (t.unlocked) {
            if (event.getTo().getZ() > event.getFrom().getZ()) {
                int oldZ = event.getFrom().getBlockZ() - worlds.getWorld().getSpawnLocation().getBlockZ();
                if (oldZ == -1521) {
                    Apostle watcher = apostles.get(player.getUniqueId());
                    if (watcher != null && watcher.hunter) {
                        Location facing = watcher.shell.getLocation();
                        facing.setDirection(player.getLocation().toVector().subtract(facing.toVector()));
                        watcher.shell.teleport(facing);
                        playApostleAnimation(watcher, "turn");
                    }
                    spawnReturnWitness(player);
                }
                if (oldZ == -1505)
                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, .55f, .48f);
                if (oldZ == -1478) removeReturnWitness(player.getUniqueId());
            }
            return;
        }
        if (t.stage == 0 && z <= -1433 && z >= -1478 && x <= 17) {
            t.stage = 1; player.sendMessage("§8The walls continue farther than they should.");
            save();
        } else if (t.stage == 1 && z <= -1521 && z >= -1530 && x <= 17) {
            t.stage = 2; player.sendMessage("§8Something is standing at the end of the corridor.");
            spawnFirstApostle(player); save();
        } else if (t.stage == 2 && z <= -1549 && z >= -1555 && x <= 5) {
            removeApostle(player.getUniqueId());
            t.stage = 3; player.sendMessage("§8The door closes behind you.");
            spawnHunter(player); save();
        } else if (t.stage == 3 && z <= -1601 && x <= 5) {
            if (t.switchActivated) {
                t.stage = 4; t.unlocked = true;
                Apostle watcher = apostles.get(player.getUniqueId());
                if (watcher != null && watcher.hunter) {
                    watcher.watching = true;
                    playApostleAnimation(watcher, "idle");
                    Location doorway = new Location(player.getWorld(), player.getWorld().getSpawnLocation().getX(),
                            plugin.getConfig().getInt("world.floor-y", FLOOR) + 1,
                            player.getWorld().getSpawnLocation().getZ() - 1597);
                    doorway.setDirection(player.getLocation().toVector().subtract(doorway.toVector()));
                    watcher.shell.teleport(doorway);
                }
                player.sendMessage("§8Beyond the wall, the world is still waiting.");
                save();
            } else player.sendMessage("§8The exit mechanism has not been activated.");
        }
        // A retreat is never treated as a restart; the same physical route remains traversable.
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwitch(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.LEVER) return;
        Player player = event.getPlayer();
        Trial t = trial(player);
        if (t.stage != 3 || !player.getWorld().equals(worlds.getWorld())) return;
        Location expected = new Location(player.getWorld(), player.getWorld().getSpawnLocation().getX() + 12,
                plugin.getConfig().getInt("world.floor-y", FLOOR) + 1,
                player.getWorld().getSpawnLocation().getZ() - 1570);
        if (event.getClickedBlock().getLocation().distanceSquared(expected) > 4) return;
        t.switchActivated = true;
        int sx = player.getWorld().getSpawnLocation().getBlockX(), sz = player.getWorld().getSpawnLocation().getBlockZ();
        int floor = plugin.getConfig().getInt("world.floor-y", FLOOR);
        for (int x = -2; x <= 2; x++) for (int y = floor + 1; y <= floor + 3; y++)
            player.getWorld().getBlockAt(sx + x, y, sz - 1600).setType(Material.AIR, false);
        player.sendMessage("§8A mechanism moves somewhere beyond the wall.");
        save();
    }

    private void spawnReturnWitness(Player player) {
        UUID id = player.getUniqueId();
        if (returnWitnesses.containsKey(id)) return;
        Location location = new Location(player.getWorld(), player.getWorld().getSpawnLocation().getX(),
                plugin.getConfig().getInt("world.floor-y", FLOOR) + 1,
                player.getWorld().getSpawnLocation().getZ() - 1512);
        String model = plugin.getConfig().getString("horror.enclosure.apostle-model", "unperson");
        ArmorStand shell = player.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true); stand.setMarker(false); stand.setSmall(false); stand.setBasePlate(false);
            stand.setArms(false); stand.setGravity(false); stand.setInvulnerable(true); stand.setSilent(true);
            stand.setCollidable(false); stand.setPersistent(false);
        });
        EntityTracker tracker = models.attachModel(shell, model).orElse(null);
        if (tracker == null) { shell.remove(); return; }
        for (Player viewer : player.getWorld().getPlayers()) if (!viewer.equals(player)) viewer.hideEntity(plugin, shell);
        Apostle witness = new Apostle(id, shell, tracker, false, player.getLocation());
        witness.watching = true;
        returnWitnesses.put(id, witness);
        playApostleAnimation(witness, "idle");
    }
    private void removeReturnWitness(UUID id) {
        Apostle witness = returnWitnesses.remove(id);
        if (witness != null) witness.remove();
    }

    private void spawnFirstApostle(Player player) {
        spawnApostle(player, false, player.getLocation().clone().add(0, 0, 12));
    }
    private void spawnHunter(Player player) {
        spawnApostle(player, true, player.getLocation().clone().add(0, 0, 8));
    }
    private void spawnApostle(Player player, boolean hunter, Location location) {
        if (apostles.containsKey(player.getUniqueId())) return;
        String model = plugin.getConfig().getString("horror.enclosure.apostle-model", "unperson");
        ArmorStand shell = player.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true); stand.setMarker(false); stand.setSmall(false); stand.setBasePlate(false);
            stand.setArms(false); stand.setGravity(false); stand.setInvulnerable(true); stand.setSilent(true);
            stand.setCollidable(false); stand.setPersistent(false);
        });
        EntityTracker tracker = models.attachModel(shell, model).orElse(null);
        if (tracker == null) { shell.remove(); plugin.getLogger().warning("Null Apostle model '" + model + "' is not loaded in BetterModel."); return; }
        for (Player viewer : player.getWorld().getPlayers()) if (!viewer.equals(player)) viewer.hideEntity(plugin, shell);
        apostles.put(player.getUniqueId(), new Apostle(player.getUniqueId(), shell, tracker, hunter, player.getLocation()));
        playApostleAnimation(apostles.get(player.getUniqueId()), hunter ? "idle" : "emerge");
    }

    private void updateApostle(Apostle a) {
        if (a.watching) return;
        Player player = plugin.getServer().getPlayer(a.target);
        if (player == null || !player.isOnline() || player.getWorld() != a.shell.getWorld() || !a.shell.isValid()) {
            removeApostle(a.target); return;
        }
        Location here = a.shell.getLocation(), target = player.getLocation();
        if (!a.hunter) {
            if (System.currentTimeMillis() - a.createdAt < 2600) return;
            moveToward(a, target, .25);
            if (here.distanceSquared(target) < 2.1) player.setHealth(0);
            return;
        }
        double distance = here.distance(target);
        long now = System.currentTimeMillis();
        if (distance < 12 && now - a.lastFootstepAt > 1700) {
            player.playSound(here, Sound.BLOCK_DEEPSLATE_BRICKS_STEP, .32f, .62f);
            a.lastFootstepAt = now;
        }
        boolean moving = player.getVelocity().lengthSquared() > .012;
        boolean sees = distance < 18 && here.getWorld().rayTraceBlocks(here.clone().add(0, 1.4, 0),
                target.clone().add(0, 1, 0).toVector().subtract(here.clone().add(0, 1.4, 0).toVector()).normalize(),
                Math.max(1, distance), FluidCollisionMode.NEVER, true) == null;
        String hideSpot = player.getLocation().getBlockX() + ":" + player.getLocation().getBlockY() + ":" + player.getLocation().getBlockZ();
        if (!moving && !sees && distance < 16) {
            if (!hideSpot.equals(a.stationarySpot)) {
                a.stationarySpot = hideSpot;
                a.stationarySince = now;
            } else if (now - a.stationarySince >= 2500 && !hideSpot.equals(a.lastRecordedHideSpot)) {
                int visits = a.hideSpotVisits.merge(hideSpot, 1, Integer::sum);
                a.lastRecordedHideSpot = hideSpot;
                if (visits >= 2) {
                    a.lastKnown = target.clone();
                    a.lastSeenAt = now;
                }
            }
        } else {
            a.stationarySpot = "";
            a.lastRecordedHideSpot = "";
            a.stationarySince = now;
        }
        boolean learnedHideSpot = a.hideSpotVisits.getOrDefault(hideSpot, 0) >= 2;
        if (learnedHideSpot && distance < 20) {
            a.lastKnown = target.clone(); a.lastSeenAt = now;
            playApostleAnimation(a, "run");
            moveToward(a, target, .17);
        } else if (sees || (moving && distance < 13)) {
            playApostleAnimation(a, "run");
            a.lastKnown = target.clone(); a.lastSeenAt = System.currentTimeMillis();
            moveToward(a, target, .17);
        } else if (System.currentTimeMillis() - a.lastSeenAt < 9000) {
            playApostleAnimation(a, "walk");
            moveToward(a, a.lastKnown, .10);
        } else {
            playApostleAnimation(a, "walk");
            if (System.currentTimeMillis() >= a.nextTurnAt || here.distanceSquared(a.lastKnown) < 2.25) {
                a.nextTurnAt = System.currentTimeMillis() + 2200 + new Random().nextInt(2600);
                Vector patrol = new Vector(Math.random() - .5, 0, Math.random() - .5).normalize();
                a.lastKnown = here.clone().add(patrol.multiply(4 + Math.random() * 3));
                a.shell.teleport(here.clone().setDirection(patrol));
            }
            moveToward(a, a.lastKnown, .06);
            if (distance < 2.2) player.setHealth(0);
        }
        if (here.distanceSquared(target) < 2.4) player.setHealth(0);
    }
    private void playApostleAnimation(Apostle apostle, String animation) {
        if (apostle == null || animation.equals(apostle.activeAnimation)) return;
        if (models.playAnimation(apostle.tracker, animation)) apostle.activeAnimation = animation;
    }

    private void moveToward(Apostle a, Location target, double speed) {
        Location here = a.shell.getLocation();
        if (target.getWorld() != here.getWorld() || !here.getChunk().isLoaded()) return;
        Vector delta = target.toVector().subtract(here.toVector()); delta.setY(0);
        if (delta.lengthSquared() < .01) return;
        Vector step = delta.normalize().multiply(speed);
        Location next = here.clone().add(step);
        if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) {
            // Investigate the last known position, but do not phase through maze walls.
            a.lastKnown = target.clone();
            return;
        }
        next.setY(here.getY());
        a.shell.teleport(next.setDirection(step));
    }
    private void removeApostle(UUID id) { Apostle a = apostles.remove(id); if (a != null) a.remove(); }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Trial t = trials.get(event.getEntity().getUniqueId());
        if (t != null && !t.unlocked && t.stage > 0) {
            t.stage = 0; t.switchActivated = false;
            event.getEntity().getPersistentDataContainer().set(trialDeathKey, PersistentDataType.BYTE, (byte) 1);
            save();
        }
        removeApostle(event.getEntity().getUniqueId());
        removeReturnWitness(event.getEntity().getUniqueId());
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Trial t = trials.get(event.getPlayer().getUniqueId());
        if (t != null && !t.unlocked && t.stage > 0) {
            t.stage = 0;
            t.switchActivated = false;
            save();
        }
        removeApostle(event.getPlayer().getUniqueId());
        removeReturnWitness(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getPersistentDataContainer().has(trialDeathKey, PersistentDataType.BYTE)) {
            Location spawn = worlds.getEntranceLocation();
            if (spawn != null) event.setRespawnLocation(spawn);
            event.getPlayer().getPersistentDataContainer().remove(trialDeathKey);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != worlds.getWorld()
                || event.getTo().getWorld() != worlds.getWorld() || hasEscaped(event.getPlayer().getUniqueId())) return;
        Location spawn = worlds.getWorld().getSpawnLocation();
        double radius = Math.hypot(event.getTo().getX() - spawn.getX(), event.getTo().getZ() - spawn.getZ());
        boolean fromMaze = Math.abs(event.getFrom().getX() - spawn.getX()) <= 38
                && event.getFrom().getZ() < spawn.getZ() - 1428
                && event.getFrom().getZ() >= spawn.getZ() - 1610;
        boolean toMaze = Math.abs(event.getTo().getX() - spawn.getX()) <= 38
                && event.getTo().getZ() < spawn.getZ() - 1428
                && event.getTo().getZ() >= spawn.getZ() - 1610;
        if (fromMaze || toMaze || radius > plugin.getConfig().getInt("horror.enclosure.radius-blocks", RADIUS) + 10) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§8The wall does not permit that passage.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == worlds.getWorld() && isEnclosureBlock(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() == worlds.getWorld() && isEnclosureArea(event.getBlock().getLocation())) event.setCancelled(true);
    }
    private boolean isEnclosureBlock(Block block) {
        int sx = block.getWorld().getSpawnLocation().getBlockX(), sz = block.getWorld().getSpawnLocation().getBlockZ();
        double radius = Math.hypot(block.getX() - sx, block.getZ() - sz);
        return Math.abs(radius - plugin.getConfig().getInt("horror.enclosure.radius-blocks", RADIUS)) <= WALL_HALF_THICKNESS + 1
                || (block.getZ() < sz - 1428 && block.getZ() >= sz - 1610
                && Math.abs(block.getX() - sx) <= 38 && block.getY() > plugin.getConfig().getInt("world.floor-y", FLOOR) - 3 && block.getY() <= plugin.getConfig().getInt("world.floor-y", FLOOR) + 12);
    }
    private boolean isEnclosureArea(Location l) {
        int sx = l.getWorld().getSpawnLocation().getBlockX(), sz = l.getWorld().getSpawnLocation().getBlockZ();
        double radius = Math.hypot(l.getBlockX() - sx, l.getBlockZ() - sz);
        return Math.abs(radius - plugin.getConfig().getInt("horror.enclosure.radius-blocks", RADIUS)) <= WALL_HALF_THICKNESS + 24
                || (l.getBlockZ() < sz - 1428 && l.getBlockZ() >= sz - 1610
                && Math.abs(l.getBlockX() - sx) <= 38 && l.getBlockY() > plugin.getConfig().getInt("world.floor-y", FLOOR) - 3 && l.getBlockY() <= plugin.getConfig().getInt("world.floor-y", FLOOR) + 12);
    }

    private void save() {
        data.set("mazes-built", mazesBuilt);
        data.set("built-perimeter-chunks", builtChunks.stream().sorted().toList());
        for (Map.Entry<UUID, Trial> entry : trials.entrySet()) {
            String id = entry.getKey().toString(); Trial t = entry.getValue();
            data.set(id + ".stage", t.stage); data.set(id + ".unlocked", t.unlocked);
            data.set(id + ".switch-activated", t.switchActivated);
        }
        try { if (!file.getParentFile().exists()) file.getParentFile().mkdirs(); data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save Null Enclosure progress: " + ex.getMessage()); }
    }

    /** Deterministic depth-first maze: connected, solvable, with a few fair loops. */
    static final class Maze {
        static char[][] generate(int cells, long seed) {
            return generate(cells, seed, cells == 7 ? 3 : 5);
        }
        static char[][] generate(int cells, long seed, int loops) {
            int n = cells * 2 + 1;
            char[][] grid = new char[n][n];
            for (char[] row : grid) Arrays.fill(row, '#');
            boolean[][] visited = new boolean[cells][cells];
            Random random = new Random(seed);
            carve(cells / 2, 0, cells, visited, grid, random);
            int centerColumn = (cells / 2) * 2 + 1;
            grid[0][centerColumn] = '.'; grid[n - 1][centerColumn] = '.';
            // A small number of extra openings prevents every maze from being a strict tree.
            for (int i = 0; i < Math.max(0, loops); i++) {
                int x = 1 + random.nextInt(n - 2), z = 1 + random.nextInt(n - 2);
                if (grid[z][x] == '#') grid[z][x] = '.';
            }
            return grid;
        }
        private static void carve(int x, int z, int cells, boolean[][] visited, char[][] grid, Random random) {
            visited[z][x] = true; grid[z * 2 + 1][x * 2 + 1] = '.';
            int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int i = directions.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1); int[] tmp = directions[i]; directions[i] = directions[j]; directions[j] = tmp;
            }
            for (int[] d : directions) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= cells || nz >= cells || visited[nz][nx]) continue;
                grid[z * 2 + 1 + d[1]][x * 2 + 1 + d[0]] = '.';
                carve(nx, nz, cells, visited, grid, random);
            }
        }
    }
}
