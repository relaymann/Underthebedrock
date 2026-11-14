package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Per-player, cooldown-governed horror events that escalate with story progress. */
public final class HorrorEventDirector {
    private final UnderTheBedrockPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> nextEvent = new HashMap<>();
    private BukkitTask task;

    public HorrorEventDirector(UnderTheBedrockPlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        nextEvent.clear();
    }

    private void tick() {
        if (!plugin.getWorlds().isActive()) return;
        World world = plugin.getWorlds().getWorld();
        if (world == null) return;
        long now = System.currentTimeMillis();
        for (Player player : world.getPlayers()) {
            long due = nextEvent.getOrDefault(player.getUniqueId(), now + 60_000L);
            if (now < due || random.nextDouble() > .22) continue;
            PlayerHorrorMemory memory = plugin.getOccurrenceDirector().memory(player.getUniqueId());
            fire(player, memory);
            long cooldown = 150_000L + random.nextInt(210_001);
            nextEvent.put(player.getUniqueId(), now + cooldown);
        }
        nextEvent.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    private void fire(Player player, PlayerHorrorMemory memory) {
        int stage = memory.discoveredClueCount() + memory.loreCount() / 2 + (int) (memory.distanceTravelled() / 4096);
        int event = random.nextInt(stage >= 8 ? 7 : stage >= 3 ? 5 : 3);
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0).normalize();
        if (direction.lengthSquared() < .01) direction = new Vector(0, 0, 1);
        Location distant = origin.clone().add(direction.rotateAroundY(Math.toRadians(random.nextBoolean() ? 90 : -90)).multiply(12 + random.nextInt(13)));
        distant.setY(origin.getY());
        switch (event) {
            case 0 -> {
                player.playSound(distant, Sound.BLOCK_STONE_STEP, .42f, .62f);
                if (random.nextBoolean()) player.playSound(distant.clone().add(0, 0, 1), Sound.BLOCK_STONE_STEP, .25f, .72f);
            }
            case 1 -> {
                player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_BASS, .2f, .45f);
                player.sendMessage("§8[LOCAL SIGNAL] §7A voice is waiting in the channel.");
            }
            case 2 -> {
                player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .18f, .5f);
                player.sendMessage("§8Your journal briefly displays an entry you have not written.");
            }
            case 3 -> {
                player.playSound(distant, Sound.BLOCK_IRON_DOOR_OPEN, .3f, .55f);
                player.sendMessage("§8Something opened a door behind the wall.");
            }
            case 4 -> {
                player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, .24f, .55f);
                player.sendMessage("§8[WORLD NOTICE] §7Identity mismatch. Please remain where you are.");
            }
            case 5 -> {
                player.playSound(distant, Sound.ENTITY_WARDEN_HEARTBEAT, .22f, .65f);
                player.sendMessage("§8You hear a second set of footsteps matching your pace.");
            }
            default -> {
                player.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, .22f, .4f);
                player.sendMessage("§8[ARCHIVE SYNC] §7A resident record has been revised.");
            }
        }
        memory.addTension(event >= 4 ? 2.2 : 1.0);
        plugin.getOccurrenceDirector().saveMemory(player.getUniqueId());
    }
}
