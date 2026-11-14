package dev.underthebedrock.world;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/** Persistent timed supply drops for the horror world. */
public final class SupplyDropManager {
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey marker;
    private BukkitTask task;
    private final Random random = new Random();

    public SupplyDropManager(UnderTheBedrockPlugin plugin, VoidWorldManager worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.file = new File(plugin.getDataFolder(), "supply-drops.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.marker = new NamespacedKey(plugin, "supply-drop");
    }

    public void start() {
        if (task != null) return;
        if (!data.contains("next-drop-at")) {
            data.set("next-drop-at", System.currentTimeMillis() + intervalMillis());
            save();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        save();
    }

    private void tick() {
        if (!worlds.isActive()) return;
        World world = worlds.getWorld();
        if (world == null || world.getPlayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now < data.getLong("next-drop-at", now + intervalMillis())) return;

        Location drop = findDropLocation(world);
        if (drop == null) {
            data.set("next-drop-at", now + Math.max(10, plugin.getConfig().getLong(
                    "horror.supply-drops.retry-seconds", 60)) * 1000L);
            save();
            return;
        }
        spawnCrate(drop);
        long sequence = data.getLong("total-drops", 0L) + 1L;
        data.set("total-drops", sequence);
        data.set("last-drop", world.getName() + "," + drop.getBlockX() + "," + drop.getBlockY() + "," + drop.getBlockZ());
        data.set("next-drop-at", now + intervalMillis());
        save();

        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "SUPPLY SIGNAL" + ChatColor.DARK_GRAY + "] "
                + ChatColor.YELLOW + "A supply crate has been detected at "
                + ChatColor.WHITE + "X: " + drop.getBlockX() + " Y: " + drop.getBlockY()
                + " Z: " + drop.getBlockZ() + ChatColor.YELLOW + ".");
        world.playSound(drop, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.65f);
        world.spawnParticle(Particle.END_ROD, drop.clone().add(0.5, 1.2, 0.5), 18, 0.35, 0.45, 0.35, 0.015);
        plugin.getLogger().info("Supply drop #" + sequence + " spawned at " + world.getName() + " "
                + drop.getBlockX() + " " + drop.getBlockY() + " " + drop.getBlockZ());
    }

    private Location findDropLocation(World world) {
        Player[] players = world.getPlayers().toArray(Player[]::new);
        if (players.length == 0) return null;
        int floor = plugin.getConfig().getInt("world.floor-y", 64) + 1;
        int min = Math.max(8, plugin.getConfig().getInt("horror.supply-drops.minimum-radius-blocks", 16));
        int max = Math.max(min + 4, plugin.getConfig().getInt("horror.supply-drops.maximum-radius-blocks", 40));
        for (int attempt = 0; attempt < 40; attempt++) {
            Player player = players[random.nextInt(players.length)];
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = min + random.nextInt(max - min + 1);
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            Block block = world.getBlockAt(x, floor, z);
            if (!block.getType().isAir() || !world.getBlockAt(x, floor + 1, z).getType().isAir()
                    || !world.getBlockAt(x, floor + 2, z).getType().isAir()) continue;
            return block.getLocation();
        }
        return null;
    }

    private void spawnCrate(Location location) {
        Block block = location.getBlock();
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Container container)) return;
        container.getInventory().clear();
        container.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        Material[] loot = {Material.BREAD, Material.COOKED_BEEF, Material.TORCH, Material.SOUL_TORCH,
                Material.IRON_INGOT, Material.GOLD_NUGGET, Material.STRING, Material.ARROW,
                Material.COMPASS, Material.CLOCK, Material.PAPER, Material.GOLDEN_CARROT,
                Material.COOKED_COD, Material.LEATHER, Material.EXPERIENCE_BOTTLE};
        int rolls = 7 + random.nextInt(5);
        for (int i = 0; i < rolls; i++) {
            Material item = loot[random.nextInt(loot.length)];
            int amount = switch (item) {
                case IRON_INGOT -> 2 + random.nextInt(5);
                case TORCH, SOUL_TORCH, ARROW, BREAD, COOKED_BEEF, COOKED_COD -> 3 + random.nextInt(6);
                case GOLD_NUGGET -> 2 + random.nextInt(7);
                default -> 1 + random.nextInt(2);
            };
            container.getInventory().addItem(new ItemStack(item, amount));
        }
        container.update(true, false);
    }

    private long intervalMillis() {
        return Math.max(1, plugin.getConfig().getLong("horror.supply-drops.interval-minutes", 30)) * 60_000L;
    }

    private void save() {
        try { data.save(file); }
        catch (IOException exception) {
            plugin.getLogger().severe("Could not save supply drop schedule: " + exception.getMessage());
        }
    }
}
