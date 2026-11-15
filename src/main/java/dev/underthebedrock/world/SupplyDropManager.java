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
        if (!spawnCrate(drop)) {
            plugin.getLogger().severe("Supply drop crate could not be populated at "
                    + drop.getBlockX() + " " + drop.getBlockY() + " " + drop.getBlockZ()
                    + "; retrying instead of announcing an empty drop.");
            data.set("next-drop-at", now + 10_000L);
            save();
            return;
        }
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

    /**
     * Creates and verifies the live block inventory. Container#getInventory can
     * represent a combined/snapshot inventory depending on the block state;
     * use the placed chest's block inventory and verify it after population.
     */
    private boolean spawnCrate(Location location) {
        Block block = location.getBlock();
        try {
            block.setType(Material.CHEST, false);
            if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
                plugin.getLogger().severe("Supply drop location did not become a chest: " + location);
                return false;
            }

            chest.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            if (!chest.update(true, false)) {
                plugin.getLogger().warning("Could not persist supply drop chest state at " + location);
                return false;
            }

            // Re-read the state after placing it so all item writes target the
            // actual block inventory, not a stale state captured before update.
            if (!(block.getState() instanceof org.bukkit.block.Chest placedChest)) return false;
            org.bukkit.inventory.Inventory inventory = placedChest.getBlockInventory();
            inventory.clear();

            // Guaranteed baseline: every successful crate has useful supplies.
            java.util.List<ItemStack> loot = new java.util.ArrayList<>();
            loot.add(new ItemStack(Material.BREAD, 4 + random.nextInt(5)));
            loot.add(new ItemStack(Material.TORCH, 8 + random.nextInt(9)));
            loot.add(new ItemStack(Material.IRON_INGOT, 3 + random.nextInt(5)));
            loot.add(new ItemStack(Material.GOLD_NUGGET, 4 + random.nextInt(7)));
            loot.add(new ItemStack(random.nextBoolean() ? Material.COOKED_BEEF : Material.GOLDEN_CARROT,
                    2 + random.nextInt(4)));

            Material[] bonusLoot = {
                    Material.SOUL_TORCH, Material.STRING, Material.ARROW, Material.COMPASS,
                    Material.CLOCK, Material.PAPER, Material.COOKED_COD, Material.LEATHER,
                    Material.EXPERIENCE_BOTTLE
            };
            int bonusRolls = 3 + random.nextInt(4);
            for (int i = 0; i < bonusRolls; i++) {
                Material item = bonusLoot[random.nextInt(bonusLoot.length)];
                int amount = item == Material.SOUL_TORCH || item == Material.ARROW
                        ? 3 + random.nextInt(6) : 1 + random.nextInt(3);
                loot.add(new ItemStack(item, amount));
            }

            java.util.Map<Integer, ItemStack> leftovers = inventory.addItem(loot.toArray(ItemStack[]::new));
            if (!leftovers.isEmpty()) {
                plugin.getLogger().warning("Some supply drop items did not fit at " + location
                        + " (leftover stacks: " + leftovers.size() + ").");
            }

            // Custom equipment is bonus loot only; it can never replace the
            // guaranteed vanilla supplies if the custom item system is absent.
            if (plugin.getCustomItems() != null) {
                int customRolls = 2 + random.nextInt(3);
                for (int i = 0; i < customRolls; i++) {
                    try {
                        ItemStack custom = plugin.getCustomItems().randomLootItem(random);
                        if (custom != null && !custom.getType().isAir()) inventory.addItem(custom);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Could not generate a custom supply item: "
                                + exception.getMessage());
                    }
                }
            }

            // Verify the placed inventory itself, not the pre-update state.
            org.bukkit.block.BlockState verifyState = block.getState();
            if (!(verifyState instanceof org.bukkit.block.Chest verifyChest)) return false;
            org.bukkit.inventory.Inventory verified = verifyChest.getBlockInventory();
            boolean hasGuaranteedLoot = verified.contains(Material.BREAD)
                    && verified.contains(Material.TORCH)
                    && verified.contains(Material.IRON_INGOT)
                    && verified.contains(Material.GOLD_NUGGET);
            if (!hasGuaranteedLoot || verified.isEmpty()) {
                plugin.getLogger().severe("Supply drop inventory verification failed at " + location
                        + "; verified contents=" + verified.getContents().length);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Failed to populate supply drop at " + location + ": "
                    + exception.getMessage());
            return false;
        }
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
