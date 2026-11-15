package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class FacilityManager implements Listener {
    private record SafeRoom(String world, int x, int y, int z, int level) {
        double radius() { return 24 + Math.max(0, level - 1) * 18; }
    }

    private final UnderTheBedrockPlugin plugin;
    private final File file;
    private final NamespacedKey districtSignKey;
    private final NamespacedKey itemKey;
    private YamlConfiguration data;
    private final Map<String, SafeRoom> safeRooms = new LinkedHashMap<>();
    private final Map<String, List<SafeRoom>> safeRoomBuckets = new HashMap<>();

    public FacilityManager(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "facility-state.yml");
        data = YamlConfiguration.loadConfiguration(file);
        districtSignKey = new NamespacedKey(plugin, "civilization-sign");
        itemKey = new NamespacedKey(plugin, "underbedrock-item");
        loadSafeRooms();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("horror.facilities.enabled", true)) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        save(data);
    }

    public boolean isInsideSafeRoom(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = location.getWorld().getName();
        int cellX = Math.floorDiv(location.getBlockX(), 256);
        int cellZ = Math.floorDiv(location.getBlockZ(), 256);
        for (int dxCell = -1; dxCell <= 1; dxCell++) for (int dzCell = -1; dzCell <= 1; dzCell++) {
            List<SafeRoom> candidates = safeRoomBuckets.get(bucketKey(worldName, cellX + dxCell, cellZ + dzCell));
            if (candidates == null) continue;
            for (SafeRoom room : candidates) {
                if (Math.abs(location.getY() - room.y()) > 14) continue;
                double dx = location.getX() - (room.x() + .5);
                double dz = location.getZ() - (room.z() + .5);
                double radius = Math.max(8, plugin.getConfig().getDouble("horror.facilities.safe-room-base-radius-blocks", 24))
                    + Math.max(0, room.level() - 1) * Math.max(4, plugin.getConfig().getDouble("horror.facilities.safe-room-upgrade-radius-blocks", 18));
                if (dx * dx + dz * dz <= radius * radius) return true;
            }
        }
        return false;
    }

    public boolean isPowerRestored(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String world = location.getWorld().getName();
        ConfigurationSection generators = data.getConfigurationSection("generators");
        if (generators == null) return false;
        for (String key : generators.getKeys(false)) {
            String path = "generators." + key;
            if (data.getBoolean(path + ".restored", false)
                    && world.equals(data.getString(path + ".world"))) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFacilityInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (!plugin.getWorlds().isActive() || !player.getWorld().equals(plugin.getWorlds().getWorld())) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;
        if (!sign.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)) return;

        String title = ChatColor.stripColor(sign.getLine(0)).trim().toUpperCase(Locale.ROOT);
        switch (title) {
            case "[GENERATOR]" -> {
                event.setCancelled(true);
                restoreGenerator(player, event.getClickedBlock().getLocation());
            }
            case "[SAFE ROOM]" -> {
                event.setCancelled(true);
                claimOrUpgradeSafeRoom(player, event.getClickedBlock().getLocation());
            }
            case "[ELEVATOR]" -> {
                event.setCancelled(true);
                rideElevator(player, event.getClickedBlock().getLocation());
            }
            case "[TERMINAL]" -> {
                event.setCancelled(true);
                readTerminal(player, event.getClickedBlock().getLocation());
            }
            default -> { }
        }
    }

    private void restoreGenerator(Player player, Location signLocation) {
        String key = key(signLocation);
        String path = "generators." + key;
        if (data.getBoolean(path + ".restored", false)) {
            player.sendMessage(ChatColor.GREEN + "The generator is already running. The lights are holding.");
            return;
        }
        int wireCost = Math.max(1, plugin.getConfig().getInt("horror.facilities.generator-requires-insulated-wire", 2));
        int fuseCost = Math.max(1, plugin.getConfig().getInt("horror.facilities.generator-requires-fuses", 2));
        int cellCost = Math.max(1, plugin.getConfig().getInt("horror.facilities.generator-requires-power-cells", 1));
        if (!hasCustom(player, "insulated_wire", wireCost) || !hasCustom(player, "fuse", fuseCost)
                || !hasCustom(player, "power_cell", cellCost)) {
            player.sendMessage(ChatColor.DARK_GRAY + "GENERATOR OFFLINE: requires 2 Insulated Wire, 2 Fuses, and 1 Power Cell.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .35f, .55f);
            return;
        }

        YamlConfiguration updated = copy(data);
        updated.set(path + ".world", signLocation.getWorld().getName());
        updated.set(path + ".x", signLocation.getBlockX());
        updated.set(path + ".z", signLocation.getBlockZ());
        updated.set(path + ".restored", true);
        updated.set(path + ".restored-at", System.currentTimeMillis());
        if (!save(updated)) {
            player.sendMessage(ChatColor.RED + "The generator state could not be saved. Your components were not consumed.");
            return;
        }
        data = updated;
        consumeCustom(player, "insulated_wire", wireCost);
        consumeCustom(player, "fuse", fuseCost);
        consumeCustom(player, "power_cell", cellCost);
        illuminate(signLocation, plugin.getConfig().getInt("horror.facilities.generator-illumination-radius-blocks", 28));
        plugin.getOccurrenceDirector().recordBehavior(player, "restored_power");
        player.sendTitle(ChatColor.GOLD + "POWER RESTORED", ChatColor.GRAY + "A distant circuit answers.", 5, 45, 15);
        player.playSound(signLocation, Sound.BLOCK_BEACON_ACTIVATE, .8f, .65f);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, signLocation.clone().add(.5, 1, .5), 34, 1.5, 1, 1.5, .04);
    }

    private void claimOrUpgradeSafeRoom(Player player, Location signLocation) {
        String key = key(signLocation);
        String path = "safe-rooms." + key;
        int current = data.getInt(path + ".level", 0);
        int maximumLevel = Math.max(1, Math.min(3, plugin.getConfig().getInt("horror.facilities.maximum-safe-room-level", 3)));
        if (current >= maximumLevel) {
            player.sendMessage(ChatColor.GREEN + "This shelter is fully reinforced. It is one of the few places the signal cannot settle.");
            return;
        }

        boolean first = current == 0;
        boolean materials = first
            ? has(player, Material.IRON_INGOT, 4) && has(player, Material.TORCH, 8)
            : hasCustom(player, "insulated_wire", 1) && hasCustom(player, "fuse", 1)
                && has(player, Material.IRON_INGOT, 4);
        if (!materials) {
            player.sendMessage(first
                ? ChatColor.DARK_GRAY + "SHELTER UNCLAIMED: requires 4 iron ingots and 8 torches."
                : ChatColor.DARK_GRAY + "REINFORCEMENT REQUIRES: 1 Insulated Wire, 1 Fuse, and 4 iron ingots.");
            return;
        }

        int nextLevel = current + 1;
        YamlConfiguration updated = copy(data);
        updated.set(path + ".world", signLocation.getWorld().getName());
        updated.set(path + ".x", signLocation.getBlockX());
        updated.set(path + ".y", signLocation.getBlockY());
        updated.set(path + ".z", signLocation.getBlockZ());
        updated.set(path + ".level", nextLevel);
        updated.set(path + ".updated-at", System.currentTimeMillis());
        if (!save(updated)) {
            player.sendMessage(ChatColor.RED + "The shelter state could not be saved. Your materials were not consumed.");
            return;
        }
        data = updated;
        if (first) {
            consume(player, Material.IRON_INGOT, 4);
            consume(player, Material.TORCH, 8);
        } else {
            consumeCustom(player, "insulated_wire", 1);
            consumeCustom(player, "fuse", 1);
            consume(player, Material.IRON_INGOT, 4);
        }
        loadSafeRooms();
        plugin.getOccurrenceDirector().recordBehavior(player, first ? "claimed_safe_room" : "reinforced_safe_room");
        player.sendMessage(ChatColor.GREEN + "SAFE ROOM " + nextLevel + "/3: protection radius " + (24 + (nextLevel - 1) * 18) + " blocks.");
        player.playSound(signLocation, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .7f, .85f);
    }

    private void rideElevator(Player player, Location signLocation) {
        int centerX = signLocation.getBlockX() - 2;
        int centerZ = signLocation.getBlockZ();
        int floorY = plugin.getConfig().getInt("world.floor-y", 64);
        World world = signLocation.getWorld();
        int topFloor = floorY + 12;
        int chunkX = Math.floorDiv(centerX, 16), chunkZ = Math.floorDiv(centerZ, 16);
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            player.sendMessage(ChatColor.RED + "The lift has no power in this section.");
            return;
        }

        for (int y = floorY + 1; y <= topFloor; y++) {
            for (int offset = -1; offset <= 1; offset++) {
                setIfReplaceable(world.getBlockAt(centerX + offset, y, centerZ - 1), Material.GLASS);
                if (!(y == floorY + 1 && offset == 0))
                    setIfReplaceable(world.getBlockAt(centerX + offset, y, centerZ + 1), Material.GLASS);
                setIfReplaceable(world.getBlockAt(centerX - 1, y, centerZ + offset), Material.GLASS);
                setIfReplaceable(world.getBlockAt(centerX + 1, y, centerZ + offset), Material.GLASS);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            setIfReplaceable(world.getBlockAt(centerX + dx, topFloor, centerZ + dz), Material.IRON_BLOCK);
        }

        Block topSignBlock = world.getBlockAt(centerX + 2, topFloor + 1, centerZ);
        if (topSignBlock.getType().isAir()) topSignBlock.setType(Material.OAK_SIGN, false);
        if (topSignBlock.getState() instanceof Sign topSign) {
            topSign.setLine(0, ChatColor.DARK_GRAY + "[ELEVATOR]");
            topSign.setLine(1, ChatColor.GRAY + "SERVICE LIFT");
            topSign.setLine(2, ChatColor.GRAY + "RIGHT-CLICK");
            topSign.getPersistentDataContainer().set(districtSignKey, PersistentDataType.BYTE, (byte) 1);
            topSign.update(true, false);
        }

        boolean upstairs = player.getLocation().getY() < topFloor;
        Location destination = new Location(world, centerX + .5, upstairs ? topFloor + 1 : floorY + 1, centerZ + .5,
            player.getLocation().getYaw(), player.getLocation().getPitch());
        if (player.teleport(destination)) {
            player.playSound(destination, Sound.BLOCK_PISTON_EXTEND, .55f, .75f);
            player.sendMessage(ChatColor.DARK_GRAY + (upstairs ? "The lift climbs into the service level." : "The lift returns to the lower platform."));
        }
    }

    private void readTerminal(Player player, Location location) {
        PlayerHorrorMemory memory = plugin.getOccurrenceDirector().memory(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "§lCIVILIAN ARCHIVE TERMINAL");
        player.sendMessage(ChatColor.GRAY + "Personnel status: " + (memory.hasLore("resident_file") ? ChatColor.RED + "ERASED / PRESENT" : ChatColor.YELLOW + "UNRESOLVED"));
        player.sendMessage(ChatColor.GRAY + "Investigation: " + ChatColor.WHITE + memory.discoveredClueCount() + "/10 evidence records");
        player.sendMessage(ChatColor.GRAY + "Memories: " + ChatColor.WHITE + memory.loreCount() + " preserved entries");
        boolean powerOnline = isPowerRestored(location);
        player.sendMessage(ChatColor.GRAY + "Civilization grid: " + (powerOnline ? ChatColor.GREEN + "ONLINE" : ChatColor.RED + "OFFLINE"));
        if (powerOnline) {
            player.sendMessage(ChatColor.AQUA + "Cached transmission recovered: " + ChatColor.GRAY + "The protection loop was still active when the city went dark.");
            plugin.getOccurrenceDirector().recordBehavior(player, "accessed_powered_terminal");
        } else {
            player.sendMessage(ChatColor.DARK_GRAY + "Only local records are available. Restore a substation to retrieve the cached transmission.");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Terminal access has been logged.");
        plugin.getOccurrenceDirector().recordBehavior(player, "researched_equipment");
        player.playSound(location, Sound.BLOCK_COMPARATOR_CLICK, .35f, .65f);
    }

    private void illuminate(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;
        int r = Math.max(4, radius);
        for (int x = -r; x <= r; x++) for (int y = -3; y <= 5; y++) for (int z = -r; z <= r; z++) {
            int bx = center.getBlockX() + x, bz = center.getBlockZ() + z;
            if (!world.isChunkLoaded(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16))) continue;
            Block block = world.getBlockAt(bx, center.getBlockY() + y, bz);
            if (block.getType() != Material.REDSTONE_LAMP || !(block.getBlockData() instanceof Lightable light) || light.isLit()) continue;
            light.setLit(true);
            block.setBlockData(light, false);
        }
    }

    private void loadSafeRooms() {
        safeRooms.clear();
        safeRoomBuckets.clear();
        ConfigurationSection section = data.getConfigurationSection("safe-rooms");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = "safe-rooms." + key;
            String world = data.getString(path + ".world");
            if (world == null) continue;
            SafeRoom room = new SafeRoom(world, data.getInt(path + ".x"), data.getInt(path + ".y"),
                data.getInt(path + ".z"), Math.max(1, Math.min(3, data.getInt(path + ".level", 1))));
            safeRooms.put(key, room);
            String bucket = bucketKey(world, Math.floorDiv(room.x(), 256), Math.floorDiv(room.z(), 256));
            safeRoomBuckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(room);
        }
    }

    private String bucketKey(String world, int x, int z) {
        return world + "_" + x + "_" + z;
    }

    private boolean nearGeneratedSite(Block block) {
        World world = block.getWorld();
        for (int dx = -5; dx <= 5; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -5; dz <= 5; dz++) {
            int x = block.getX() + dx, y = block.getY() + dy, z = block.getZ() + dz;
            if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            if (world.getBlockAt(x, y, z).getState() instanceof Sign sign
                    && sign.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAncientSiteBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getWorlds().isActive() || !player.getWorld().equals(plugin.getWorlds().getWorld())) return;
        Material type = event.getBlock().getType();
        if (type != Material.CRYING_OBSIDIAN && type != Material.RESPAWN_ANCHOR
                && type != Material.SPAWNER && type != Material.LECTERN && type != Material.CHEST) return;
        if (!nearGeneratedSite(event.getBlock())) return;
        plugin.getOccurrenceDirector().recordBehavior(player,
            type == Material.CHEST ? "stole_artifact" : "destroyed_ancient_site");
        if (player.getLocation().distanceSquared(event.getBlock().getLocation()) <= 16 * 16) {
            player.sendMessage(ChatColor.DARK_GRAY + "Something remembers the sound of that breaking.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShelterConstruction(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getWorlds().isActive() || !player.getWorld().equals(plugin.getWorlds().getWorld())) return;
        Material type = event.getBlockPlaced().getType();
        if (type == Material.COBBLESTONE || type == Material.STONE_BRICKS || type == Material.OAK_PLANKS
                || type == Material.SPRUCE_PLANKS || type == Material.TORCH || type == Material.LANTERN) {
            plugin.getOccurrenceDirector().recordBehavior(player, "built_shelter");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArtifactPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getWorlds().isActive() || !player.getWorld().equals(plugin.getWorlds().getWorld())) return;
        ItemStack stack = event.getItem().getItemStack();
        if (!stack.hasItemMeta()) return;
        String id = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) return;
        if (id.equals("null_fragment") || id.equals("resonance_core") || id.equals("architect_seal")
                || id.equals("power_cell") || id.equals("reconstructor")) {
            plugin.getOccurrenceDirector().recordBehavior(player, "recovered_artifact");
        }
    }

    private boolean has(Player player, Material material, int amount) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) found += stack.getAmount();
            if (found >= amount) return true;
        }
        return false;
    }

    private void consume(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() != material) continue;
            int removed = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - removed);
            remaining -= removed;
            if (remaining <= 0) break;
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean hasCustom(Player player, String id, int amount) {
        return customCount(player, id) >= amount;
    }

    private int customCount(Player player, String id) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isCustom(stack, id)) count += stack.getAmount();
        }
        return count;
    }

    private void consumeCustom(Player player, String id, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (!isCustom(stack, id)) continue;
            int removed = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - removed);
            remaining -= removed;
            if (remaining <= 0) break;
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean isCustom(ItemStack stack, String id) {
        return stack != null && stack.hasItemMeta()
            && id.equals(stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    private void setIfReplaceable(Block block, Material material) {
        if (block.getType().isAir() || block.getType() == Material.GLASS || block.getType() == Material.IRON_BLOCK) {
            block.setType(material, false);
        }
    }

    private String key(Location location) {
        return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockZ();
    }

    private YamlConfiguration copy(YamlConfiguration source) {
        YamlConfiguration result = new YamlConfiguration();
        try {
            result.loadFromString(source.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Facility state could not be copied safely.", exception);
        }
        return result;
    }

    private boolean save(YamlConfiguration configuration) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            File temp = new File(parent, file.getName() + ".tmp");
            configuration.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save facility state: " + exception.getMessage());
            return false;
        }
    }
}
