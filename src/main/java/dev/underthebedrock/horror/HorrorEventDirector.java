package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Player-specific psychological phenomena. Effects are deliberately brief, non-destructive,
 * and paced independently for each player. No real player chat or identity is spoofed.
 */
public final class HorrorEventDirector {
    private final UnderTheBedrockPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> nextEvent = new HashMap<>();
    private final Map<UUID, String> currentRegion = new HashMap<>();
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
        currentRegion.clear();
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("horror.events.enabled", true)
                || !plugin.getWorlds().isActive()) return;
        World world = plugin.getWorlds().getWorld();
        if (world == null) return;

        long now = System.currentTimeMillis();
        int cellSize = Math.max(32, plugin.getConfig().getInt("horror.events.region-cell-size-blocks", 128));
        for (Player player : world.getPlayers()) {
            UUID id = player.getUniqueId();
            PlayerHorrorMemory memory = plugin.getOccurrenceDirector().memory(id);
            String region = Math.floorDiv(player.getLocation().getBlockX(), cellSize) + ":"
                    + Math.floorDiv(player.getLocation().getBlockZ(), cellSize);
            if (!region.equals(currentRegion.put(id, region)) && memory.recordRegion(region)) {
                memory.addTension(.35);
                plugin.getOccurrenceDirector().saveMemory(id);
            }

            long firstDue = now + Math.max(30, plugin.getConfig().getInt(
                    "horror.events.minimum-cooldown-seconds", 150)) * 1000L;
            long due = nextEvent.computeIfAbsent(id, ignored -> (long) firstDue);
            if (now < due || random.nextDouble() > .22) continue;

            fire(player, memory);
            int minimum = Math.max(30, plugin.getConfig().getInt("horror.events.minimum-cooldown-seconds", 150));
            int maximum = Math.max(minimum, plugin.getConfig().getInt("horror.events.maximum-cooldown-seconds", 360));
            long cooldown = (minimum + random.nextInt(maximum - minimum + 1)) * 1000L;
            nextEvent.put(id, now + cooldown);
            plugin.getOccurrenceDirector().saveMemory(id);
        }

        nextEvent.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
        currentRegion.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    private void fire(Player player, PlayerHorrorMemory memory) {
        WorldRecognitionStage stage = memory.recognitionStage();
        int maximum = stage.maximumPhenomenonIndex();
        int event = chooseEvent(maximum, memory.lastPhenomenon());
        Location origin = player.getLocation().clone();
        Vector direction = origin.getDirection().setY(0).normalize();
        if (direction.lengthSquared() < .01) direction = new Vector(0, 0, 1);
        Vector side = direction.clone().rotateAroundY(Math.toRadians(random.nextBoolean() ? 90 : -90));
        Location distant = origin.clone().add(side.multiply(12 + random.nextInt(13)));
        distant.setY(origin.getY());
        Location behind = origin.clone().subtract(direction.multiply(4 + random.nextInt(3)));
        behind.setY(origin.getY());

        switch (event) {
            case 0 -> {
                player.playSound(distant, Sound.BLOCK_STONE_STEP, .34f, .62f);
                if (random.nextBoolean()) player.playSound(distant.clone().add(0, 0, 1),
                        Sound.BLOCK_STONE_STEP, .2f, .72f);
            }
            case 1 -> {
                player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_BASS, .18f, .45f);
                whisper(player, "A voice is waiting in the channel.");
            }
            case 2 -> {
                player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .16f, .5f);
                whisper(player, "Your journal contains a line you do not remember writing.");
            }
            case 3 -> {
                player.playSound(distant, Sound.BLOCK_IRON_DOOR_OPEN, .26f, .55f);
                whisper(player, "Something opened a door behind the wall.");
            }
            case 4 -> {
                player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, .2f, .55f);
                whisper(player, "Identity mismatch. Please remain where you are.");
            }
            case 5 -> {
                player.playSound(distant, Sound.ENTITY_WARDEN_HEARTBEAT, .2f, .65f);
                player.playSound(behind, Sound.BLOCK_STONE_STEP, .22f, .58f);
                whisper(player, "A second set of footsteps is matching your pace.");
            }
            case 6 -> {
                player.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, .2f, .4f);
                whisper(player, "A resident record has been revised.");
            }
            case 7 -> {
                Player nearby = nearestOther(player, 48);
                if (nearby == null) {
                    player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, .18f, .5f);
                    whisper(player, "Someone just said your name.");
                } else {
                    player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, .18f, .5f);
                    whisper(player, "You hear " + nearby.getName() + " whisper from somewhere nearby.");
                    nearby.playSound(nearby.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .12f, .42f);
                }
            }
            case 8 -> {
                player.playSound(distant, Sound.BLOCK_WOODEN_DOOR_OPEN, .24f, .55f);
                for (int i = 1; i <= 2; i++) {
                    final int knock = i;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getWorld().equals(origin.getWorld())) {
                            player.playSound(distant, Sound.BLOCK_WOODEN_DOOR_OPEN, .2f, .55f + knock * .08f);
                        }
                    }, i * 9L);
                }
                whisper(player, "Three knocks. They came from inside the wall.");
            }
            case 9 -> {
                Location light = distant.clone().add(0, 1.2, 0);
                player.spawnParticle(Particle.SMOKE, light, 16, .35, .6, .35, .008);
                player.playSound(distant, Sound.BLOCK_BEACON_POWER_SELECT, .16f, .45f);
                whisper(player, "A light was visible out there. It is gone now.");
            }
            case 10 -> {
                player.playSound(origin, Sound.BLOCK_CHEST_CLOSE, .18f, .52f);
                whisper(player, "You recognize this place. You have never been here.");
            }
            case 11 -> {
                player.playSound(behind, Sound.BLOCK_STONE_STEP, .24f, .7f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && player.getWorld().equals(origin.getWorld())) {
                        player.playSound(behind, Sound.BLOCK_STONE_STEP, .2f, .58f);
                    }
                }, 12L);
                whisper(player, "The footsteps continued after you stopped.");
            }
            case 12 -> {
                player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .18f, .38f);
                whisper(player, "Your coordinates do not agree with the marks on the floor.");
            }
            case 13 -> {
                player.playSound(origin, Sound.ENTITY_WARDEN_HEARTBEAT, .25f, .42f);
                whisper(player, "For a moment, there was one person too many.");
            }
            case 14 -> {
                Player nearby = nearestOther(player, 32);
                player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_BASS, .15f, .35f);
                whisper(player, nearby == null
                        ? "Someone is standing where your shadow should be."
                        : "You both heard something. You are certain it was not the same thing.");
                if (nearby != null) nearby.playSound(nearby.getLocation(),
                        Sound.ENTITY_ENDERMAN_STARE, .14f, .72f);
            }
            default -> {
                player.playSound(origin, Sound.BLOCK_DEEPSLATE_BREAK, .3f, .35f);
                whisper(player, "Something moved underneath the floor.");
            }
        }

        memory.recordPhenomenon("phenomenon_" + event, System.currentTimeMillis(),
                event >= 8 ? 1.8 : 1.0);
    }

    private int chooseEvent(int maximum, String previous) {
        int event = random.nextInt(maximum + 1);
        if (maximum > 1 && ("phenomenon_" + event).equals(previous)) {
            event = (event + 1 + random.nextInt(maximum)) % (maximum + 1);
        }
        return event;
    }

    private Player nearestOther(Player player, double radius) {
        return player.getWorld().getPlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .filter(other -> other.getLocation().distanceSquared(player.getLocation()) <= radius * radius)
                .min((a, b) -> Double.compare(a.getLocation().distanceSquared(player.getLocation()),
                        b.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private void whisper(Player player, String message) {
        player.sendMessage(Component.text("… " + message, NamedTextColor.DARK_GRAY));
    }
}
