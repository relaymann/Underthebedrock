package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Generates abandoned districts and archaeological traces of the erased population.
 * No living residents, active settlements, or moving civilization infrastructure are spawned.
 * It only generates in already-loaded chunks near players and never force-loads terrain.
 */
public final class CivilizationManager implements Listener {
    private final UnderTheBedrockPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey residentKey;
    private final NamespacedKey districtSignKey;
    private final Random random = new Random();
    private BukkitTask task;

    private enum Site {
        RESIDENTIAL, STATION, MEDICAL, CANTEEN, ARCHIVE, POWER
    }

    public CivilizationManager(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();
        file = new File(folder, "civilization-sites.yml");
        data = YamlConfiguration.loadConfiguration(file);
        residentKey = new NamespacedKey(plugin, "civilization-resident");
        districtSignKey = new NamespacedKey(plugin, "civilization-sign");
    }

    public void start() {
        if (task != null) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long interval = Math.max(40, plugin.getConfig().getLong("horror.civilization.scan-interval-ticks", 100));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 80, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        save();
    }

    private void tick() {
        if (!plugin.getWorlds().isActive()) return;
        World world = plugin.getWorlds().getWorld();
        if (world == null) return;
        int cell = Math.max(512, plugin.getConfig().getInt("horror.civilization.site-cell-size-blocks", 2048));
        for (Player player : world.getPlayers()) {
            int cx = Math.floorDiv(player.getLocation().getBlockX() - world.getSpawnLocation().getBlockX(), cell);
            int cz = Math.floorDiv(player.getLocation().getBlockZ() - world.getSpawnLocation().getBlockZ(), cell);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                ensureSite(world, cx + dx, cz + dz, dx == 0 && dz == 0 ? player : null);
            purgeLegacyResidents(player);
            ambient(player);
        }
    }

    private void ensureSite(World world, int cx, int cz, Player explorer) {
        String key = cx + "_" + cz;
        String path = "sites." + key;
        long seed = world.getSeed() ^ ((long) cx * 0x9E3779B97F4A7C15L)
            ^ ((long) cz * 0xC2B2AE3D27D4EB4FL) ^ 0x6A09E667F3BCC909L;
        Random rng = new Random(seed);
        if (!data.contains(path + ".type")) {
            if (explorer == null || distanceFromSpawn(world, explorer.getLocation()) < minimumSpawnDistance()
                    || rng.nextDouble() > plugin.getConfig().getDouble("horror.civilization.cell-generation-chance", 0.16)) return;
            int[] candidate = siteCandidate(world, explorer, rng);
            int x = candidate[0], z = candidate[1];
            Site type = Site.values()[rng.nextInt(Site.values().length)];
            if (!loaded(world, x, z, 18) || !clear(world, x, plugin.getConfig().getInt("world.floor-y", 64) + 1, z, 17)) return;
            data.set(path + ".type", type.name());
            data.set(path + ".x", x);
            data.set(path + ".z", z);
            data.set(path + ".built", false);
            save();
        }
        Site type;
        try { type = Site.valueOf(data.getString(path + ".type", "RESIDENTIAL")); }
        catch (IllegalArgumentException ex) { return; }
        int x = data.getInt(path + ".x"), z = data.getInt(path + ".z");
        int y = plugin.getConfig().getInt("world.floor-y", 64) + 1;
        if (!data.getBoolean(path + ".built", false) && explorer != null
                && explorer.getLocation().distanceSquared(new Location(world, x, y, z)) > 180 * 180) {
            if (distanceFromSpawn(world, explorer.getLocation()) < minimumSpawnDistance()) return;
            int[] candidate = siteCandidate(world, explorer, rng);
            x = candidate[0]; z = candidate[1];
            if (!loaded(world, x, z, 18) || !clear(world, x, y, z, 17)) return;
            data.set(path + ".x", x);
            data.set(path + ".z", z);
            save();
        }
        Location center = new Location(world, x + .5, y, z + .5);
        boolean nearby = world.getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(center) < 160 * 160);
        if (!nearby || !loaded(world, x, z, 18)) return;
        if (!data.getBoolean(path + ".built", false)) {
            if (!clear(world, x, y, z, 17)) return;
            build(world, x, y - 1, z, type, new Random(seed));
            data.set(path + ".built", true);
            save();
        }
        // These sites are archaeological remains. No resident NPCs or moving services survive.
    }

    private double minimumSpawnDistance() {
        return Math.max(0, plugin.getConfig().getDouble("horror.civilization.minimum-distance-from-spawn-blocks", 600));
    }

    private double distanceFromSpawn(World world, Location location) {
        Location spawn = world.getSpawnLocation();
        return Math.hypot(location.getX() - spawn.getX(), location.getZ() - spawn.getZ());
    }

    private int[] siteCandidate(World world, Player explorer, Random rng) {
        Location player = explorer.getLocation(), spawn = world.getSpawnLocation();
        int minimum = Math.max(64, plugin.getConfig().getInt("horror.civilization.minimum-site-offset-blocks", 220));
        int maximum = Math.max(minimum, plugin.getConfig().getInt("horror.civilization.maximum-site-offset-blocks", 360));
        int offset = minimum + rng.nextInt(maximum - minimum + 1);
        double angle = rng.nextDouble() * Math.PI * 2;
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * offset);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * offset);
        if (Math.hypot(x - spawn.getX(), z - spawn.getZ()) < minimumSpawnDistance()) {
            angle = Math.atan2(player.getZ() - spawn.getZ(), player.getX() - spawn.getX());
            if (Math.abs(player.getX() - spawn.getX()) + Math.abs(player.getZ() - spawn.getZ()) < 1) {
                angle = rng.nextDouble() * Math.PI * 2;
            }
            x = player.getBlockX() + (int) Math.round(Math.cos(angle) * offset);
            z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * offset);
        }
        return new int[]{x, z};
    }

    private boolean loaded(World world, int x, int z, int radius) {
        for (int cx = Math.floorDiv(x - radius, 16); cx <= Math.floorDiv(x + radius, 16); cx++)
            for (int cz = Math.floorDiv(z - radius, 16); cz <= Math.floorDiv(z + radius, 16); cz++)
                if (!world.isChunkLoaded(cx, cz)) return false;
        return true;
    }

    private boolean clear(World world, int x, int y, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
            for (int dy = 0; dy <= 7; dy++)
                if (!world.getBlockAt(x + dx, y + dy, z + dz).getType().isAir()) return false;
        return true;
    }

    private void build(World world, int x, int y, int z, Site type, Random rng) {
        Builder b = new Builder(world, x, y, z);
        switch (type) {
            case RESIDENTIAL -> {
                b.room(-8, 8, -7, 7, Material.BRICKS, Material.OAK_PLANKS);
                b.put(-5, 1, -3, Material.RED_BED); b.put(5, 1, -3, Material.WHITE_BED);
                b.put(-5, 1, 3, Material.BOOKSHELF); b.put(5, 1, 3, Material.CHEST);
                b.put(0, 1, 0, Material.CRAFTING_TABLE); b.put(0, 1, 2, Material.FLOWER_POT);
                b.sign(0, 2, -7, "RESIDENTIAL BLOCK", "HOUSEHOLD 04", "STATUS: PRESENT", "STATUS: [ERASED]");
                b.loreSign(-7, 2, 6, "resident_file", "PERSONAL RESIDENT FILE", "NAME: CURRENT RETURN");
                b.loreSign(7, 2, 6, "population_ledger", "HOUSEHOLD LEDGER", "COUNT DOES NOT MATCH");
                b.chest(-6, 1, 5, rng, true); b.memoryShard(-6, 1, 5, "last_shift");
            }
            case STATION -> {
                b.platform(-9, 9, -8, 8, Material.DEEPSLATE_TILES);
                for (int railZ = -7; railZ <= 7; railZ++) {
                    b.put(-2, 1, railZ, Material.RAIL); b.put(2, 1, railZ, Material.RAIL);
                    if (railZ % 4 == 0) { b.put(-2, 0, railZ, Material.REDSTONE_BLOCK); b.put(2, 0, railZ, Material.REDSTONE_BLOCK); }
                }
                b.put(-2, 1, -7, Material.POWERED_RAIL); b.put(2, 1, 7, Material.POWERED_RAIL); b.put(-3, 1, -7, Material.REDSTONE_TORCH); b.put(3, 1, 7, Material.REDSTONE_TORCH);
                b.put(-7, 1, 0, Material.SOUL_LANTERN); b.put(7, 1, 0, Material.SOUL_LANTERN);
                b.sign(0, 2, -8, "CENTRAL STATION", "PLATFORM 02", "ARRIVALS: 1", "DEPARTURES: 0");
                b.sign(0, 2, 8, "[TERMINAL]", "SERVICE ARCHIVE", "RIGHT-CLICK TO READ");
                b.elevator(6, 0, 0);
                b.loreSign(8, 2, 7, "last_day", "FINAL SERVICE LOG", "NO DEPARTURES");
                b.chest(5, 1, 5, rng, true); b.memoryShard(5, 1, 5, "final_hour");
            }
            case MEDICAL -> {
                b.room(-8, 8, -7, 7, Material.QUARTZ_BLOCK, Material.IRON_BLOCK);
                b.put(-5, 1, -3, Material.WHITE_BED); b.put(0, 1, -3, Material.WHITE_BED);
                b.put(5, 1, -3, Material.WHITE_BED); b.put(0, 1, 2, Material.BREWING_STAND);
                b.put(-5, 1, 3, Material.CAULDRON); b.put(5, 1, 3, Material.LECTERN);
                b.sign(0, 2, -7, "MEDICAL WING", "PATIENTS: 12", "DISCHARGED: 0", "MISSING: 12");
                b.sign(0, 2, 8, "[SAFE ROOM]", "SHELTER UNCLAIMED", "RIGHT-CLICK TO REINFORCE");
                b.loreSign(-7, 2, 6, "last_day", "PATIENT REPORT", "NAMES REMOVED");
                b.chest(6, 1, 5, rng, false); b.memoryShard(6, 1, 5, "protection_loop");
            }
            case CANTEEN -> {
                b.room(-9, 9, -6, 6, Material.SMOOTH_SANDSTONE, Material.SPRUCE_PLANKS);
                for (int x0 = -5; x0 <= 5; x0 += 5) for (int z0 = -2; z0 <= 2; z0 += 4) {
                    b.put(x0, 1, z0, Material.OAK_PRESSURE_PLATE);
                    b.put(x0, 0, z0, Material.OAK_PLANKS);
                }
                b.put(-7, 1, 4, Material.FURNACE); b.put(7, 1, 4, Material.BARREL);
                b.sign(0, 2, -6, "CIVILIAN CANTEEN", "LUNCH SERVICE 13:00", "LAST MEAL: STILL WARM");
                b.loreSign(8, 2, 5, "decades_silence", "AUTOMATED LOG", "NO STAFF DETECTED");
                b.chest(-7, 1, 5, rng, false); b.memoryShard(-7, 1, 5, "final_hour");
            }
            case ARCHIVE -> {
                b.room(-9, 9, -8, 8, Material.DEEPSLATE_BRICKS, Material.CHISELED_BOOKSHELF);
                for (int x0 = -7; x0 <= 7; x0 += 2) {
                    b.put(x0, 1, -5, Material.BOOKSHELF); b.put(x0, 2, -5, Material.BOOKSHELF);
                    b.put(x0, 1, 5, Material.BOOKSHELF); b.put(x0, 2, 5, Material.BOOKSHELF);
                }
                b.put(0, 1, 0, Material.LECTERN); b.put(-3, 1, 0, Material.CHEST); b.put(3, 1, 0, Material.CHEST);
                b.sign(0, 2, -8, "CIVILIAN ARCHIVE", "PERSONNEL INDEX", "THE INDEX DENIES", "ITS OWN EXISTENCE");
                b.put(0, 1, 1, Material.BLACK_CONCRETE);
                b.put(-1, 1, 1, Material.IRON_BLOCK); b.put(1, 1, 1, Material.IRON_BLOCK);
                b.put(0, 2, 1, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                b.sign(0, 2, 8, "[TERMINAL]", "PERSONNEL DATABASE", "RIGHT-CLICK TO ACCESS");
                b.loreSign(-8, 2, 7, "resident_file", "PERSONAL RECORD", "RETURNING SUBJECT");
                b.loreSign(8, 2, 7, "decades_silence", "ARCHIVE MAINTENANCE", "LAST CYCLE: YEARS AGO");
                b.chest(-7, 1, 7, rng, true); b.memoryShard(-7, 1, 7, "identity_fragment");
            }
            case POWER -> {
                b.platform(-7, 7, -7, 7, Material.POLISHED_BLACKSTONE);
                for (int dx = -4; dx <= 4; dx += 4) for (int dz = -4; dz <= 4; dz += 4) {
                    b.put(dx, 1, dz, Material.IRON_BLOCK); b.put(dx, 2, dz, Material.LIGHTNING_ROD);
                }
                b.put(0, 1, 0, Material.REDSTONE_LAMP); b.put(0, 1, 2, Material.LEVER);
                b.sign(0, 2, -7, "POWER SUBSTATION", "GRID: OFFLINE", "AUXILIARY: RUNNING", "OPERATOR: UNKNOWN");
                b.sign(0, 2, 4, "[GENERATOR]", "GRID OFFLINE", "RIGHT-CLICK TO RESTORE");
                b.loreSign(6, 2, 6, "protection_system", "EMERGENCY POWER", "ANCHOR CIRCUIT ACTIVE");
                b.chest(-5, 1, 5, rng, false); b.memoryShard(-5, 1, 5, "protection_loop");
            }
        }
    }

    /** Remove resident echoes and automated service carts left by earlier plugin versions. */
    private void purgeLegacyResidents(Player player) {
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 128, 32, 128)) {
            if (entity instanceof Villager && entity.getPersistentDataContainer().has(residentKey, PersistentDataType.STRING)) {
                entity.remove();
                continue;
            }
            if (entity instanceof Minecart && entity.getCustomName() != null
                    && entity.getCustomName().contains("SERVICE 02")) entity.remove();
        }
    }

    private void ambient(Player player) {
        if (!plugin.getConfig().getBoolean("horror.civilization.ambient-effects-enabled", true)) return;
        PlayerHorrorMemory memory = plugin.getOccurrenceDirector().memory(player.getUniqueId());
        int phase = memory.hasLore("recognition_event") ? 5 : memory.hasLore("orphaned_identity") ? 4
            : memory.hasLore("resident_mara") || memory.hasLore("resident_elias") || memory.hasLore("resident_june") || memory.hasLore("resident_tomas") ? 3
            : memory.hasLore("resident_file") ? 2 : 1;
        double chance = .035 + phase * .009;
        if (random.nextDouble() > chance) return;
        Location at = player.getLocation();
        switch (random.nextInt(5)) {
            case 0 -> {
                Location behind = at.clone().subtract(at.getDirection().normalize().multiply(4));
                if (behind.getWorld().isChunkLoaded(behind.getBlockX() >> 4, behind.getBlockZ() >> 4)) {
                    player.playSound(behind, Sound.BLOCK_WOODEN_DOOR_OPEN, .25f + phase * .035f, .55f);
                    behind.getWorld().spawnParticle(Particle.SMOKE, behind.clone().add(0, 1, 0), 3, .2, .3, .2, .005);
                    openNearbyDoor(behind);
                }
            }
            case 1 -> player.playSound(at, Sound.BLOCK_TRIPWIRE_CLICK_ON, .18f + phase * .025f, .45f);
            case 2 -> player.playSound(at.clone().add(0, 0, 7), Sound.BLOCK_STONE_STEP, .22f, .65f);
            case 3 -> {
                player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, .14f, .35f);
                at.getWorld().spawnParticle(Particle.ASH, at.clone().add(0, 2, 0), 5, .35, .3, .35, .005);
                if (phase >= 3) flickerNearbyLight(at);
            }
            default -> {
                if (phase >= 3) {
                    player.playSound(at, Sound.ENTITY_ENDERMAN_STARE, .13f, .5f);
                    at.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0, 1, 0), 8, .4, .5, .4, .02);
                    glitchNearbySign(at);
                }
            }
        }
        memory.addTension(.25 + phase * .12);
    }

    private void flickerNearbyLight(Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        for (int dx = -12; dx <= 12; dx++) for (int dy = -3; dy <= 5; dy++) for (int dz = -12; dz <= 12; dz++) {
            int bx = origin.getBlockX() + dx, by = origin.getBlockY() + dy, bz = origin.getBlockZ() + dz;
            if (!world.isChunkLoaded(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16))) continue;
            Block block = world.getBlockAt(bx, by, bz);
            if (block.getType() != Material.REDSTONE_LAMP || !(block.getBlockData() instanceof Lightable light) || !light.isLit()) continue;
            Lightable off = (Lightable) block.getBlockData();
            off.setLit(false);
            String temporaryState = off.getAsString();
            block.setBlockData(off, false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (block.getType() == Material.REDSTONE_LAMP
                        && block.getBlockData() instanceof Lightable current
                        && current.getAsString().equals(temporaryState)) {
                    current.setLit(true);
                    block.setBlockData(current, false);
                }
            }, 4L);
            return;
        }
    }

    private void openNearbyDoor(Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int bx = origin.getBlockX() + dx, bz = origin.getBlockZ() + dz;
            if (!world.isChunkLoaded(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16))) continue;
            Block block = world.getBlockAt(bx, origin.getBlockY(), bz);
            String type = block.getType().name();
            if (!(type.endsWith("_DOOR") || type.endsWith("_TRAPDOOR") || type.endsWith("_FENCE_GATE"))) continue;
            if (!(block.getBlockData() instanceof Openable openable) || openable.isOpen()) continue;
            openable.setOpen(true);
            String temporaryState = openable.getAsString();
            block.setBlockData(openable, false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (block.getBlockData() instanceof Openable current
                        && current.isOpen() && current.getAsString().equals(temporaryState)) {
                    current.setOpen(false);
                    block.setBlockData(current, false);
                }
            }, 24L);
            return;
        }
    }

    private void glitchNearbySign(Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        for (int dx = -10; dx <= 10; dx++) for (int dy = -2; dy <= 4; dy++) for (int dz = -10; dz <= 10; dz++) {
            int bx = origin.getBlockX() + dx, by = origin.getBlockY() + dy, bz = origin.getBlockZ() + dz;
            if (!world.isChunkLoaded(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16))) continue;
            Block block = world.getBlockAt(bx, by, bz);
            if (!(block.getState() instanceof Sign sign)
                    || !sign.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)) continue;
            String[] original = sign.getLines();
            sign.setLine(0, ChatColor.DARK_RED + "YOU WERE HERE");
            sign.setLine(1, ChatColor.DARK_RED + "YOU NEVER LEFT");
            sign.update(true, false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (block.getState() instanceof Sign current
                        && current.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)
                        && (ChatColor.DARK_RED + "YOU WERE HERE").equals(current.getLine(0))
                        && (ChatColor.DARK_RED + "YOU NEVER LEFT").equals(current.getLine(1))
                        && original[2].equals(current.getLine(2))
                        && original[3].equals(current.getLine(3))) {
                    for (int line = 0; line < 4; line++) current.setLine(line, original[line]);
                    current.update(true, false);
                }
            }, 40L);
            return;
        }
    }

    private final class Builder {
        private final World world;
        private final int ox, oy, oz;
        Builder(World world, int x, int y, int z) { this.world = world; ox = x; oy = y; oz = z; }
        void put(int dx, int dy, int dz, Material material) {
            Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
            if (block.getType().isAir()) block.setType(material, false);
        }
        void platform(int minX, int maxX, int minZ, int maxZ, Material material) {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) put(x, 0, z, material);
        }
        void room(int minX, int maxX, int minZ, int maxZ, Material wall, Material trim) {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                put(x, 0, z, Material.POLISHED_ANDESITE);
                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    put(x, 1, z, wall); put(x, 2, z, wall); put(x, 3, z, trim);
                }
            }
        }
        private int signY(int x, int y, int z) {
            return world.getBlockAt(ox + x, oy + 1, oz + z).getType().isAir() ? 1 : Math.max(2, y);
        }
        void sign(int x, int y, int z, String... lines) {
            int sy = signY(x, y, z);
            Block block = world.getBlockAt(ox + x, oy + sy, oz + z);
            block.setType(Material.OAK_SIGN, false);
            if (block.getState() instanceof Sign sign) {
                for (int i = 0; i < 4; i++) sign.setLine(i, i < lines.length ? ChatColor.DARK_GRAY + lines[i] : "");
                sign.getPersistentDataContainer().set(districtSignKey, PersistentDataType.BYTE, (byte) 1);
                sign.update(true, false);
            }
        }
        void loreSign(int x, int y, int z, String id, String... lines) {
            int sy = signY(x, y, z);
            sign(x, y, z, lines);
            Block block = world.getBlockAt(ox + x, oy + sy, oz + z);
            if (block.getState() instanceof Sign sign) {
                plugin.getLoreManager().tagDocument(sign, id);
                sign.update(true, false);
            }
        }
        void elevator(int x, int y, int z) {
            for (int height = 1; height <= 12; height++) {
                put(x - 1, height, z - 1, Material.GLASS);
                put(x + 1, height, z - 1, Material.GLASS);
                put(x - 1, height, z + 1, Material.GLASS);
                put(x + 1, height, z + 1, Material.GLASS);
            }
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) put(x + dx, 12, z + dz, Material.IRON_BLOCK);
            sign(x + 2, 2, z, "[ELEVATOR]", "SERVICE LIFT", "RIGHT-CLICK TO RIDE");
        }
        void memoryShard(int x, int y, int z, String recording) {
            Block block = world.getBlockAt(ox + x, oy + y, oz + z);
            if (block.getState() instanceof Container container) {
                container.getInventory().addItem(plugin.getLoreManager().createMemoryShard(recording));
                container.update(true, false);
            }
        }
        void chest(int x, int y, int z, Random rng, boolean transport) {
            put(x, y, z, Material.CHEST);
            Block block = world.getBlockAt(ox + x, oy + y, oz + z);
            if (!(block.getState() instanceof Container container)) return;
            if (transport) container.getInventory().addItem(new ItemStack(Material.MINECART));
            container.getInventory().addItem(new ItemStack(Material.BREAD, 2 + rng.nextInt(3)));
            container.getInventory().addItem(new ItemStack(Material.PAPER, 1 + rng.nextInt(4)));
            if (rng.nextBoolean()) container.getInventory().addItem(new ItemStack(Material.TORCH, 4));
            container.update(true, false);
        }
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save civilization sites: " + ex.getMessage()); }
    }
}
