package dev.underthebedrock;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/** Crash-safe reset: deletion is deferred until next startup, before managers cache state. */
public final class FullResetService {
    private static final String MARKER = "reset.pending";
    private static final Set<String> PRESERVED = Set.of(
        "config.yml", "legacy-inventory-backups.yml", "player-isolation.yml", "backups"
    );

    private FullResetService() {}

    public static boolean request(JavaPlugin plugin) {
        Path data = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(data);
            Files.writeString(data.resolve(MARKER), Instant.now().toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Bukkit.broadcastMessage("§c[Under The Bedrock] A full world reset has been scheduled. The server is shutting down.");
            Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 40L);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not schedule full reset: " + exception.getMessage());
            return false;
        }
    }

    public static boolean processPending(JavaPlugin plugin) {
        Path data = plugin.getDataFolder().toPath();
        Path marker = data.resolve(MARKER);
        if (!Files.exists(marker)) return true;

        String worldName = plugin.getConfig().getString("world.name", "under_the_bedrock");
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldFolder = container.resolve(worldName).normalize();
        if (!worldFolder.startsWith(container) || worldFolder.equals(container)) {
            plugin.getLogger().severe("Full reset refused: configured world path is unsafe.");
            return false;
        }

        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now());
        Path backup = data.resolve("backups").resolve("reset-" + stamp);
        try {
            World loaded = Bukkit.getWorld(worldName);
            if (loaded != null && !Bukkit.unloadWorld(loaded, false)) {
                plugin.getLogger().severe("Full reset paused: could not safely unload " + worldName + ".");
                return false;
            }
            Files.createDirectories(backup);
            if (Files.exists(worldFolder)) copyTree(worldFolder, backup.resolve("world"));
            copyPluginData(data, backup.resolve("plugin-data"));
            if (Files.exists(worldFolder)) deleteTree(worldFolder);

            try (DirectoryStream<Path> children = Files.newDirectoryStream(data)) {
                for (Path child : children) {
                    if (PRESERVED.contains(child.getFileName().toString())) continue;
                    deleteTree(child);
                }
            }
            Files.deleteIfExists(marker);
            plugin.getLogger().warning("Full reset completed. Backup: " + backup.toAbsolutePath());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Full reset failed; pending marker retained: " + exception.getMessage());
            return false;
        }
    }

    private static void copyPluginData(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (path.equals(source)) continue;
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 0 && relative.getName(0).toString().equals("backups")) continue;
                if (relative.toString().equals(MARKER)) continue;
                Path target = destination.resolve(relative);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
