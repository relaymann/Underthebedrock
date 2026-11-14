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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Adds the lived-in civilization layer: deterministic districts, resident memory echoes,
 * rail infrastructure, player-specific relationships, and restrained environmental scares.
 * It only generates in already-loaded chunks near players and never force-loads terrain.
 */
public final class CivilizationManager implements Listener {
    private static final int CELL = 1024;
    private final UnderTheBedrockPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey residentKey;
    private final NamespacedKey echoKey;
    private final NamespacedKey districtSignKey;
    private final Random random = new Random();
    private BukkitTask task;

    private enum Site {
        RESIDENTIAL, STATION, MEDICAL, CANTEEN, ARCHIVE, POWER
    }

    private record Resident(String id, String name, String role, String first, String later, String finalLine) {}

    private static final List<Resident> RESIDENTS = List.of(
        new Resident("mara", "Mara Venn", "Transit clerk",
            "You know that name. I used to save you a seat on the late train. You always said you were only going one stop.",
            "Your employee file says you were a civilian resident. Mine says I was supposed to forget you. I couldn't.",
            "I waited at Platform Two after the lights went out. The board kept displaying your arrival. No train came."),
        new Resident("elias", "Elias Rook", "Research technician",
            "You brought the first signal logs to my desk. You told me the static sounded like someone breathing.",
            "The researchers called it a response pattern. You called it listening. You were right, and we were late.",
            "The removal order listed you before the rest of us. The system crossed your name out, then left the room occupied."),
        new Resident("june", "June Aster", "Archive keeper",
            "I remember your handwriting. You used to leave notes in books and put them back on the wrong shelf.",
            "I hid a copy of your resident record. The official archive says you never existed. The paper disagrees.",
            "If you are hearing this, the archive did not keep you safe. It only kept proof that you were here."),
        new Resident("tomas", "Tomas Vale", "Medical attendant",
            "You came through the medical wing once with a split hand. You apologized for getting blood on the forms.",
            "There was no illness. No evacuation. People vanished while their monitors still showed a pulse.",
            "I kept your chart open because your name kept returning after the system erased it. Then the monitors went quiet.")
    );

    public CivilizationManager(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();
        file = new File(folder, "civilization-sites.yml");
        data = YamlConfiguration.loadConfiguration(file);
        residentKey = new NamespacedKey(plugin, "civilization-resident");
        echoKey = new NamespacedKey(plugin, "civilization-echo");
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
        for (Player player : world.getPlayers()) {
            int cx = Math.floorDiv(player.getLocation().getBlockX() - world.getSpawnLocation().getBlockX(), CELL);
            int cz = Math.floorDiv(player.getLocation().getBlockZ() - world.getSpawnLocation().getBlockZ(), CELL);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) ensureSite(world, cx + dx, cz + dz);
            ambient(player);
        }
    }

    private void ensureSite(World world, int cx, int cz) {
        String key = cx + "_" + cz;
        String path = "sites." + key;
        long seed = world.getSeed() ^ ((long) cx * 0x9E3779B97F4A7C15L) ^ ((long) cz * 0xC2B2AE3D27D4EB4FL) ^ 0x6A09E667F3BCC909L;
        Random rng = new Random(seed);
        if (!data.contains(path + ".type")) {
            if (rng.nextDouble() > plugin.getConfig().getDouble("horror.civilization.cell-generation-chance", 0.20)) return;
            int x = world.getSpawnLocation().getBlockX() + cx * CELL + CELL / 2 + (rng.nextBoolean() ? 1 : -1) * (320 + rng.nextInt(120));
            int z = world.getSpawnLocation().getBlockZ() + cz * CELL + CELL / 2 + (rng.nextBoolean() ? 1 : -1) * (320 + rng.nextInt(120));
            Site type = Site.values()[rng.nextInt(Site.values().length)];
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
        Location center = new Location(world, x + .5, y, z + .5);
        boolean nearby = world.getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(center) < 72 * 72);
        if (!nearby || !loaded(world, x, z, 18)) return;
        if (!data.getBoolean(path + ".built", false)) {
            if (!clear(world, x, y, z, 17)) return;
            build(world, x, y - 1, z, type, new Random(seed));
            data.set(path + ".built", true);
            save();
        }
        if (plugin.getConfig().getBoolean("horror.civilization.resident-echoes-enabled", true) && (type == Site.RESIDENTIAL || type == Site.STATION || type == Site.MEDICAL || type == Site.ARCHIVE || type == Site.POWER)) {
            Resident resident = switch (type) {
                case RESIDENTIAL, STATION -> RESIDENTS.get(0);
                case ARCHIVE -> RESIDENTS.get(2);
                case MEDICAL -> RESIDENTS.get(3);
                case POWER -> RESIDENTS.get(1);
                default -> RESIDENTS.get(Math.floorMod((int) (seed ^ (seed >>> 32)), RESIDENTS.size()));
            };
            spawnResident(world, new Location(world, x + .5, y, z + .5), resident);
        }
        if (type == Site.STATION) spawnTrainIfDue(world, x, y, z, path);
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
                b.loreSign(8, 2, 7, "last_day", "FINAL SERVICE LOG", "NO DEPARTURES");
                b.chest(5, 1, 5, rng, true); b.memoryShard(5, 1, 5, "final_hour");
            }
            case MEDICAL -> {
                b.room(-8, 8, -7, 7, Material.QUARTZ_BLOCK, Material.IRON_BLOCK);
                b.put(-5, 1, -3, Material.WHITE_BED); b.put(0, 1, -3, Material.WHITE_BED);
                b.put(5, 1, -3, Material.WHITE_BED); b.put(0, 1, 2, Material.BREWING_STAND);
                b.put(-5, 1, 3, Material.CAULDRON); b.put(5, 1, 3, Material.LECTERN);
                b.sign(0, 2, -7, "MEDICAL WING", "PATIENTS: 12", "DISCHARGED: 0", "MISSING: 12");
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
                b.loreSign(6, 2, 6, "protection_system", "EMERGENCY POWER", "ANCHOR CIRCUIT ACTIVE");
                b.chest(-5, 1, 5, rng, false); b.memoryShard(-5, 1, 5, "protection_loop");
            }
        }
    }

    private void spawnTrainIfDue(World world, int x, int y, int z, String path) {
        long now = System.currentTimeMillis();
        if (now - data.getLong(path + ".last-train", 0) < 8 * 60_000L) return;
        Location start = new Location(world, x - 1.5, y + .05, z - 6.5);
        if (world.getNearbyEntities(start, 5, 3, 5).stream().anyMatch(e -> e instanceof Minecart)) return;
        Minecart train = world.spawn(start, Minecart.class, cart -> {
            cart.setSlowWhenEmpty(false);
            cart.setMaxSpeed(.35);
            cart.setVelocity(new org.bukkit.util.Vector(0, 0, .22));
            cart.setCustomName(ChatColor.DARK_GRAY + "SERVICE 02");
            cart.setCustomNameVisible(true);
            cart.setPersistent(false);
        });
        train.setVelocity(new org.bukkit.util.Vector(0, 0, .22));
        data.set(path + ".last-train", now);
        save();
    }

    private void spawnResident(World world, Location location, Resident resident) {
        for (Entity entity : world.getNearbyEntities(location, 12, 5, 12)) {
            if (entity.getPersistentDataContainer().has(residentKey, PersistentDataType.STRING)
                    && resident.id().equals(entity.getPersistentDataContainer().get(residentKey, PersistentDataType.STRING))) return;
        }
        Location spawn = location.clone().add(0, 0, 3);
        Villager villager = world.spawn(spawn, Villager.class, v -> {
            v.setAI(false); v.setInvulnerable(true); v.setSilent(true); v.setCollidable(false);
            v.setPersistent(true); v.setRemoveWhenFarAway(false); v.setProfession(Villager.Profession.LIBRARIAN);
            v.setCustomName(ChatColor.GRAY + resident.name() + ChatColor.DARK_GRAY + " · " + resident.role());
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(residentKey, PersistentDataType.STRING, resident.id());
            v.getPersistentDataContainer().set(echoKey, PersistentDataType.BYTE, (byte) 1);
        });
        villager.setVillagerLevel(1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onResidentInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        String id = entity.getPersistentDataContainer().get(residentKey, PersistentDataType.STRING);
        if (id == null) return;
        Player player = event.getPlayer();
        if (!plugin.getWorlds().isActive() || !player.getWorld().equals(plugin.getWorlds().getWorld())) return;
        event.setCancelled(true);
        Resident resident = RESIDENTS.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
        if (resident == null) return;
        PlayerHorrorMemory memory = plugin.getOccurrenceDirector().memory(player.getUniqueId());
        String loreId = "resident_" + id;
        boolean first = memory.discoverLore(loreId);
        String line = first ? resident.first() : memory.hasLore("orphaned_identity") ? resident.finalLine() : resident.later();
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "§l" + resident.name().toUpperCase(Locale.ROOT));
        for (String part : wrap(line.replace("[player]", player.getName()), 66))
            player.sendMessage(ChatColor.GRAY + "§o" + part);
        if (first) {
            memory.addTension(4.5);
            plugin.getOccurrenceDirector().saveMemory(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .55f, .65f);
            player.sendMessage(ChatColor.DARK_PURPLE + "A familiar voice has been preserved in your personal archive.");
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .25f, .55f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void protectResidentEcho(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(echoKey, PersistentDataType.BYTE))
            event.setCancelled(true);
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
            Block block = world.getBlockAt(origin.getBlockX() + dx, origin.getBlockY() + dy, origin.getBlockZ() + dz);
            if (block.getType() != Material.REDSTONE_LAMP || !(block.getBlockData() instanceof Lightable light) || !light.isLit()) continue;
            Lightable off = (Lightable) block.getBlockData();
            off.setLit(false);
            block.setBlockData(off, false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (block.getType() == Material.REDSTONE_LAMP && block.getBlockData() instanceof Lightable current) {
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
            Block block = world.getBlockAt(origin.getBlockX() + dx, origin.getBlockY(), origin.getBlockZ() + dz);
            String type = block.getType().name();
            if (!(type.endsWith("_DOOR") || type.endsWith("_TRAPDOOR") || type.endsWith("_FENCE_GATE"))) continue;
            if (!(block.getBlockData() instanceof Openable openable) || openable.isOpen()) continue;
            openable.setOpen(true);
            block.setBlockData(openable, false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (block.getBlockData() instanceof Openable current && current.isOpen()) {
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
            Block block = world.getBlockAt(origin.getBlockX() + dx, origin.getBlockY() + dy, origin.getBlockZ() + dz);
            if (!(block.getState() instanceof Sign sign)
                    || !sign.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)) continue;
            String[] original = sign.getLines();
            sign.setLine(0, ChatColor.DARK_RED + "YOU WERE HERE");
            sign.setLine(1, ChatColor.DARK_RED + "YOU NEVER LEFT");
            sign.update(true, false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (block.getState() instanceof Sign current
                        && current.getPersistentDataContainer().has(districtSignKey, PersistentDataType.BYTE)) {
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

    private List<String> wrap(String input, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : input.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString()); line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save civilization sites: " + ex.getMessage()); }
    }
}
