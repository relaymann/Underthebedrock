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
        this.active = plugin.getConfig().getBoolean("world.enabled-on-startup", false);
    }

    public boolean start() {
        if (ensureWorld() == null) return false;
        active = true;
        plugin.getConfig().set("world.enabled-on-startup", true);
        plugin.saveConfig();
        return true;
    }

    public boolean stop() {
        World current = getWorld();
        if (current != null && !current.getPlayers().isEmpty()) return false;
        active = false;
        plugin.getConfig().set("world.enabled-on-startup", false);
        plugin.saveConfig();
        if (current != null) {
            Bukkit.unloadWorld(current, true);
            world = null;
        }
        return true;
    }

    public World ensureWorld() {
        if (world != null) return world;
        String name = plugin.getConfig().getString("world.name", "under_the_bedrock");
        int floorY = plugin.getConfig().getInt("world.floor-y", 64);
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) { world = loaded; return world; }

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

        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(plugin.getConfig().getLong("world.time", 18000L));
        world.setStorm(false);
        world.setThundering(false);
        int x = plugin.getConfig().getInt("world.spawn-x", 0);
        int z = plugin.getConfig().getInt("world.spawn-z", 0);
        world.getChunkAt(Math.floorDiv(x, 16), Math.floorDiv(z, 16)).load();
        world.setSpawnLocation(x, floorY + 1, z);
        plugin.getLogger().info("Void world created: " + name + " (bedrock Y=" + floorY + ")");
        return world;
    }

    public World getWorld() {
        if (world == null) world = Bukkit.getWorld(plugin.getConfig().getString("world.name", "under_the_bedrock"));
        return world;
    }
    public boolean isActive() { return active; }
}
