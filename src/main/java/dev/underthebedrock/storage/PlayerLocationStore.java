package dev.underthebedrock.storage;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class PlayerLocationStore {
    private final UnderTheBedrockPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public PlayerLocationStore(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("player.return-location-file", "player-locations.yml"));
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void saveReturnLocation(Player player, Location location) {
        String key = player.getUniqueId().toString();
        data.set(key + ".world", location.getWorld() == null ? null : location.getWorld().getName());
        data.set(key + ".x", location.getX());
        data.set(key + ".y", location.getY());
        data.set(key + ".z", location.getZ());
        data.set(key + ".yaw", location.getYaw());
        data.set(key + ".pitch", location.getPitch());
        flush();
    }

    public Location getReturnLocation(UUID id) {
        String key = id.toString();
        String name = data.getString(key + ".world");
        if (name == null) return null;
        World world = Bukkit.getWorld(name);
        if (world == null && new File(Bukkit.getWorldContainer(), name).isDirectory()) {
            world = Bukkit.createWorld(new WorldCreator(name));
        }
        if (world == null) return null;
        return new Location(world, data.getDouble(key + ".x"), data.getDouble(key + ".y"),
            data.getDouble(key + ".z"), (float) data.getDouble(key + ".yaw"), (float) data.getDouble(key + ".pitch"));
    }

    public void clearReturnLocation(UUID id) {
        runSync(() -> {
            data.set(id.toString(), null);
            flush();
        });
    }

    public void runSync(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    public void flush() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create player location storage directory.");
            return;
        }
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save return locations: " + ex.getMessage()); }
    }
}
