package dev.underthebedrock.world;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

public final class VoidWorldManager {
    private final UnderTheBedrockPlugin plugin;
    private World world;
    private boolean active;

    public VoidWorldManager(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        this.active = false;
    }

    public boolean start() {
        if (ensureWorld() == null) return false;
        active = true;
        plugin.getConfig().set("world.enabled-on-startup", true);
        plugin.getConfig().set("world.auto-start", true);
        plugin.saveConfig();
        return true;
    }

    public boolean stop() {
        World current = getWorld();
        if (current != null && !current.getPlayers().isEmpty()) return false;
        if (current != null && !Bukkit.unloadWorld(current, true)) return false;
        active = false;
        plugin.getConfig().set("world.enabled-on-startup", false);
        plugin.getConfig().set("world.auto-start", false);
        plugin.saveConfig();
        world = null;
        return true;
    }

    public World ensureWorld() {
        String name = plugin.getConfig().getString("world.name", "under_the_bedrock");
        int floorY = plugin.getConfig().getInt("world.floor-y", 64);
        if (world != null) { applyWorldRules(world, floorY); return world; }
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) { world = loaded; applyWorldRules(world, floorY); return world; }

        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(false);
        creator.generator((ChunkGenerator) new BedrockVoidGenerator(floorY));
        world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Bukkit could not create/load world " + name);
            return null;
        }

        applyWorldRules(world, floorY);
        plugin.getLogger().info("Void world created: " + name + " (bedrock Y=" + floorY + ")");
        return world;
    }

    private void applyWorldRules(World target, int floorY) {
        target.setDifficulty(Difficulty.NORMAL);
        target.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        target.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        target.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        target.setGameRule(GameRule.KEEP_INVENTORY, true);
        target.setTime(plugin.getConfig().getLong("world.time", 18000L));
        target.setStorm(false);
        target.setThundering(false);
        setEntranceSpawn(target, floorY);
    }

    private void setEntranceSpawn(World target, int floorY) {
        int x = plugin.getConfig().getInt("world.spawn-x", 0);
        int z = plugin.getConfig().getInt("world.spawn-z", 0);
        target.getChunkAt(Math.floorDiv(x, 16), Math.floorDiv(z, 16)).load();
        if (target.getBlockAt(x, floorY, z).getType() != Material.BEDROCK) target.getBlockAt(x, floorY, z).setType(Material.BEDROCK, false);
        target.setSpawnLocation(x, floorY + 1, z);
    }

    public Location getEntranceLocation() {
        World target = ensureWorld();
        if (target == null) return null;
        return new Location(target, plugin.getConfig().getInt("world.spawn-x", 0) + 0.5,
                plugin.getConfig().getInt("world.floor-y", 64) + 1,
                plugin.getConfig().getInt("world.spawn-z", 0) + 0.5);
    }

    public World getWorld() {
        if (world == null) world = Bukkit.getWorld(plugin.getConfig().getString("world.name", "under_the_bedrock"));
        return world;
    }
    public boolean isActive() { return active; }
}
