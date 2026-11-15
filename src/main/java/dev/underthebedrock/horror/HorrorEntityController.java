package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.integration.BetterModelIntegration;
import dev.underthebedrock.world.VoidWorldManager;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Player-specific apparition and behavior director. Apparitions never force-load chunks,
 * never share their shell with other players, and use bounded movement rather than teleporting
 * directly to a player's live position.
 */
public final class HorrorEntityController implements Listener {
    private enum State { OBSERVING, STALKING, SEARCHING, HUNTING, RETREATING, DISAPPEARING }

    private static final class Encounter {
        private final UUID target;
        private final EntityArchetype type;
        private final ArmorStand shell;
        private final long startedAt;
        private EntityTracker modelTracker;
        private boolean mimicRevealed;
        private boolean mimicRevealWarningLogged;
        private String activeAnimation = "";
        private long animationUntil;
        private boolean disappearanceStarted;
        private long mimicLastAttackAt;
        private Location lastKnown;
        private Location lastPlayerPosition;
        private State state = State.OBSERVING;
        private int unseenChecks;
        private int observedChecks;

        private Encounter(UUID target, EntityArchetype type, ArmorStand shell, Location lastKnown, EntityTracker modelTracker) {
            this.target = target;
            this.type = type;
            this.shell = shell;
            this.lastKnown = lastKnown.clone();
            this.lastPlayerPosition = lastKnown.clone();
            this.startedAt = System.currentTimeMillis();
            this.modelTracker = modelTracker;
        }
    }

    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final OccurrenceDirector director;
    private final BetterModelIntegration models;
    private final Map<UUID, Encounter> active = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;
    private boolean warned;

    public HorrorEntityController(UnderTheBedrockPlugin plugin, VoidWorldManager worlds,
                                  OccurrenceDirector director, BetterModelIntegration models) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.director = director;
        this.models = models;
    }

    public void start() {
        if (task != null) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long ticks = Math.max(1, plugin.getConfig().getLong("horror.director.check-interval-seconds", 12)) * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        clearAll();
    }

    private void tick() {
        if (!worlds.isActive()) { clearAll(); return; }
        World world = worlds.getWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            Encounter encounter = active.get(player.getUniqueId());
            if (encounter == null) attempt(player);
            else update(player, encounter);
        }
        active.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.getWorld().equals(world)) return false;
            remove(entry.getValue());
            return true;
        });
    }

    private void attempt(Player player) {
        if (models.availableModels().isEmpty()) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("No BetterModel models registered; entity encounters paused.");
            }
            return;
        }
        Location spawn = findSpawn(player);
        if (spawn == null) return;
        var selected = director.trySelectEntityEncounter(player);
        if (selected.isEmpty()) return;
        EntityArchetype type = selected.get();
        String key = type.name().toLowerCase(java.util.Locale.ROOT);
        String model = plugin.getConfig().getString("horror.entities.models." + key,
                plugin.getConfig().getString("integrations.bettermodel.default-watcher-model", "watcher"));
        if (!models.availableModels().contains(model)) {
            String fallback = plugin.getConfig().getString("integrations.bettermodel.default-watcher-model", "watcher");
            if (!models.availableModels().contains(fallback)) {
                plugin.getLogger().warning("No registered BetterModel model for " + type + " or fallback '" + fallback + "'.");
                return;
            }
            model = fallback;
        }

        ArmorStand shell = player.getWorld().spawn(spawn, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setCustomNameVisible(false);
        });
        var tracker = models.attachModel(shell, model);
        if (tracker.isEmpty()) {
            shell.remove();
            plugin.getLogger().warning("Could not attach BetterModel '" + model + "' for " + type + ".");
            return;
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) viewer.hideEntity(plugin, shell);
        }
        Encounter encounter = new Encounter(player.getUniqueId(), type, shell, player.getLocation(), tracker.get());
        active.put(player.getUniqueId(), encounter);
        setAnimation(encounter, "emerge");
        director.recordEntityEncounter(player, type);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, .12f, .55f);
    }

    private Location findSpawn(Player player) {
        Location origin = player.getLocation();
        Vector forward = origin.getDirection().setY(0).normalize();
        if (forward.lengthSquared() < .01) forward = new Vector(0, 0, 1);
        int floor = plugin.getConfig().getInt("world.floor-y", 64);
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(105 + random.nextDouble() * 150);
            Vector offset = forward.clone().rotateAroundY(angle).multiply(26 + random.nextDouble() * 38);
            int x = origin.getBlockX() + offset.getBlockX();
            int z = origin.getBlockZ() + offset.getBlockZ();
            if (!player.getWorld().isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            int y = floor + 1;
            if (random.nextDouble() < .18) y += 2;
            Location location = new Location(player.getWorld(), x + .5, y, z + .5);
            if (location.distanceSquared(origin) >= 400 && location.getBlock().isPassable()) return location;
        }
        return null;
    }

    private void update(Player player, Encounter encounter) {
        State previousState = encounter.state;
        ArmorStand shell = encounter.shell;
        if (!shell.isValid() || !shell.getWorld().equals(player.getWorld())) {
            remove(encounter);
            active.remove(player.getUniqueId());
            return;
        }
        Location here = shell.getLocation();
        double distanceSquared = here.distanceSquared(player.getLocation());
        Location playerNow = player.getLocation();
        double playerMovementSquared = encounter.lastPlayerPosition == null ? 0 : encounter.lastPlayerPosition.distanceSquared(playerNow);
        encounter.lastPlayerPosition = playerNow.clone();
        boolean visible = distanceSquared < 10000 && player.hasLineOfSight(shell);
        if (visible) {
            encounter.observedChecks++;
            encounter.unseenChecks = 0;
            encounter.state = State.OBSERVING;
        } else {
            encounter.unseenChecks++;
            encounter.lastKnown = player.getLocation().clone();
        }

        if (distanceSquared > 16384 || System.currentTimeMillis() - encounter.startedAt > 180000) {
            encounter.state = State.DISAPPEARING;
        }

        switch (encounter.type) {
            case WATCHER -> {
                // The Watcher does not pursue: it holds its distant position and leaves once noticed repeatedly.
                if (visible && encounter.observedChecks >= 2) encounter.state = State.DISAPPEARING;
            }
            case WITNESS, OBSERVER, FORGOTTEN_RESIDENT, SURVEYOR, UNPERSON, ORIGINAL -> {
                if (visible && distanceSquared < 3600) encounter.state = State.DISAPPEARING;
            }
            case PEEKER, DOORFRAME, LURKER, CEILING_CRAWLER -> {
                if (visible) encounter.state = State.RETREATING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case CRAWLER -> {
                int lightLimit = plugin.getConfig().getInt("horror.entities.behavior.crawler-maximum-light-level", 7);
                boolean dark = shell.getLocation().getBlock().getLightLevel() <= lightLimit;
                if (dark && distanceSquared < 3600) encounter.state = visible ? State.SEARCHING : State.HUNTING;
                else if (visible) encounter.state = State.RETREATING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case PURSUER, AGGRESSOR, CRAWLING_ONE, HEAVY, EXCAVATOR -> {
                double provocation = director.memory(player.getUniqueId()).provocation();
                if (provocation >= 12 || distanceSquared < 1600) encounter.state = visible ? State.SEARCHING : State.HUNTING;
                else if (visible) encounter.state = State.OBSERVING;
            }
            case MIMIC -> {
                double provocation = director.memory(player.getUniqueId()).provocation();
                boolean readyToStrike = provocation >= 12 || (distanceSquared <= 100 && encounter.unseenChecks >= 2);
                if (!encounter.mimicRevealed && readyToStrike) revealMimic(player, encounter);
                if (encounter.mimicRevealed) {
                    encounter.lastKnown = player.getLocation().clone();
                    encounter.state = State.HUNTING;
                } else if (visible) {
                    encounter.state = State.OBSERVING;
                } else if (encounter.unseenChecks >= 2) {
                    encounter.state = State.SEARCHING;
                    // It imitates the player's footfalls while the disguise is still intact.
                    if (playerMovementSquared >= 0.36 && random.nextDouble() < 0.45) {
                        player.playSound(shell.getLocation(), Sound.BLOCK_STONE_STEP, 0.24f, 0.62f + random.nextFloat() * 0.22f);
                    } else if (random.nextDouble() < 0.10) {
                        player.playSound(shell.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 0.16f, 0.72f);
                    }
                }
            }
            case IMITATOR, DOUBLE -> {
                if (visible) encounter.state = State.OBSERVING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.SEARCHING;
            }
            case ECHO -> {
                if (!visible && encounter.unseenChecks % 2 == 0) player.playSound(player.getLocation(), Sound.BLOCK_STONE_STEP, .16f, .65f);
                if (visible && distanceSquared < 2500) encounter.state = State.DISAPPEARING;
            }
            case MISPRINT, ABSENCE -> {
                if (visible && encounter.observedChecks >= 2) encounter.state = State.DISAPPEARING;
            }
            case DRIFTER -> {
                if (!visible && encounter.unseenChecks >= 2 && random.nextDouble() < .38) encounter.state = State.STALKING;
                else if (visible) encounter.state = State.OBSERVING;
            }
            case WANDERER, DEEP_ENTITY -> {
                if (!visible && random.nextDouble() < .16) encounter.state = State.STALKING;
            }
            case STALKER -> encounter.state = visible ? State.OBSERVING : State.STALKING;
        }

        if (encounter.state == State.HUNTING && previousState != State.HUNTING) {
            if (encounter.type == EntityArchetype.HEAVY || encounter.type == EntityArchetype.DEEP_ENTITY
                    || encounter.type == EntityArchetype.AGGRESSOR || encounter.type == EntityArchetype.EXCAVATOR) {
                setAnimation(encounter, "roar");
            } else if (encounter.type == EntityArchetype.CRAWLER || encounter.type == EntityArchetype.CRAWLING_ONE
                    || encounter.type == EntityArchetype.PURSUER) {
                setAnimation(encounter, "lunge");
            }
        }

        if (encounter.state == State.DISAPPEARING) {
            if (!encounter.disappearanceStarted) {
                encounter.disappearanceStarted = true;
                setAnimation(encounter, "disappear");
                shell.getWorld().spawnParticle(Particle.SMOKE, shell.getLocation().add(0, 1, 0), 18, .25, .5, .25, .025);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    remove(encounter);
                    active.remove(encounter.target, encounter);
                }, 25L);
            }
            return;
        }
        boolean oneShotPlaying = encounter.animationUntil > System.currentTimeMillis();
        if (!oneShotPlaying) {
            String desired;
            if (encounter.state == State.HUNTING) {
                desired = "run";
            } else if (encounter.state == State.STALKING) {
                desired = locomotionAnimation(encounter);
            } else if (encounter.state == State.RETREATING) {
                desired = "run";
            } else if (encounter.state == State.SEARCHING) {
                double cue = random.nextDouble();
                desired = cue < .22 ? "listen" : cue < .40 ? "inspect" : locomotionAnimation(encounter);
            } else {
                double cue = random.nextDouble();
                desired = cue < .08 ? "twitch" : cue < .18 ? "listen" : cue < .24 ? "inspect" : "idle";
            }
            setAnimation(encounter, desired);
        }
        if (encounter.state == State.RETREATING) {
            moveAway(shell, player.getLocation(), encounter.type == EntityArchetype.CEILING_CRAWLER ? 2.2 : 1.4);
            if (distanceSquared > 10000) { remove(encounter); active.remove(player.getUniqueId()); }
            return;
        }
        if (encounter.state == State.STALKING) {
            if (encounter.type == EntityArchetype.DOORFRAME && encounter.unseenChecks == 2) {
                Location behind = player.getLocation().subtract(player.getLocation().getDirection().setY(0).normalize().multiply(12));
                behind.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
                teleportIfLoaded(shell, behind);
            } else {
                double step = encounter.type == EntityArchetype.CEILING_CRAWLER ? 1.1
                    : encounter.type == EntityArchetype.CRAWLER ? 0.82
                    : encounter.type == EntityArchetype.DRIFTER ? 0.42 : .55;
            moveToward(shell, encounter.lastKnown, step);
            }
            return;
        }
        if (encounter.state == State.HUNTING) {
            if (encounter.type == EntityArchetype.MIMIC && distanceSquared <= 4.0
                    && System.currentTimeMillis() - encounter.mimicLastAttackAt >= 3000) {
                encounter.mimicLastAttackAt = System.currentTimeMillis();
                setAnimation(encounter, "attack");
                player.damage(4.0, shell);
                Vector knockback = player.getLocation().toVector().subtract(shell.getLocation().toVector());
                if (knockback.lengthSquared() > .01) {
                    knockback.normalize().multiply(.62).setY(.24);
                    player.setVelocity(knockback);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, .9f, .72f);
                player.playSound(shell.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, .65f, .62f);
                return;
            }
            double speed = encounter.type == EntityArchetype.HEAVY ? .35
                    : encounter.type == EntityArchetype.CRAWLING_ONE ? 1.8
                    : encounter.type == EntityArchetype.CRAWLER ? 1.15
                    : encounter.type == EntityArchetype.MIMIC ? 1.45 : 1.0;
            moveToward(shell, encounter.lastKnown, speed);
            return;
        }
        if (encounter.state == State.SEARCHING && !visible) {
            moveToward(shell, encounter.lastKnown, encounter.type == EntityArchetype.DOUBLE ? .8 : .65);
            return;
        }
        if ((encounter.type == EntityArchetype.WANDERER || encounter.type == EntityArchetype.DRIFTER)
                && random.nextDouble() < (encounter.type == EntityArchetype.DRIFTER ? .18 : .12)) {
            Location roam = shell.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            roam.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
            teleportIfLoaded(shell, roam);
        }
    }

    private void revealMimic(Player player, Encounter encounter) {
        String revealedModel = plugin.getConfig().getString("horror.entities.models.mimic-revealed", "mimic_revealed");
        if (!models.availableModels().contains(revealedModel)) {
            if (!encounter.mimicRevealWarningLogged) {
                encounter.mimicRevealWarningLogged = true;
                plugin.getLogger().warning("Mimic is ready to reveal, but BetterModel '" + revealedModel + "' is not registered.");
            }
            return;
        }
        models.replaceModel(encounter.shell, encounter.modelTracker, revealedModel).ifPresent(tracker -> {
            encounter.modelTracker = tracker;
            encounter.mimicRevealed = true;
            encounter.state = State.HUNTING;
            encounter.activeAnimation = "";
            setAnimation(encounter, "reveal");
            Location at = encounter.shell.getLocation();
            encounter.shell.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 1, 0), 42, .38, .72, .38, .035);
            encounter.shell.getWorld().spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 24, .28, .55, .28, .12);
            player.playSound(at, Sound.ENTITY_ENDERMAN_SCREAM, 1.25f, .58f);
            player.playSound(at, Sound.ENTITY_WARDEN_ROAR, .85f, .72f);
        });
    }


    private void setAnimation(Encounter encounter, String animation) {
        long now = System.currentTimeMillis();
        if (animation.equals(encounter.activeAnimation)
                && (encounter.animationUntil == 0 || now < encounter.animationUntil)) return;
        if (!models.playAnimation(encounter.modelTracker, animation)) return;

        encounter.activeAnimation = animation;
        long duration = switch (animation) {
            case "attack" -> 1050L;
            case "lunge" -> 720L;
            case "twitch" -> 580L;
            case "reveal" -> 1250L;
            case "disappear" -> 1200L;
            case "emerge" -> 1650L;
            case "hurt" -> 620L;
            case "death" -> 1800L;
            case "roar" -> 1350L;
            case "inspect" -> 1550L;
            case "listen" -> 1050L;
            case "recoil" -> 680L;
            case "turn" -> 1000L;
            default -> 0L;
        };
        encounter.animationUntil = duration == 0L ? 0L : now + duration;

        // One-shot clips hand control back to the current locomotion pose on their exact clip duration.
        // The callback is encounter-guarded so stale clips cannot override a newer state or tracker.
        if (duration > 0L && !animation.equals("disappear") && !animation.equals("death")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!encounter.shell.isValid()
                        || active.get(encounter.target) != encounter
                        || !animation.equals(encounter.activeAnimation)
                        || encounter.state == State.DISAPPEARING) return;
                encounter.activeAnimation = "";
                setAnimation(encounter, loopAnimation(encounter));
            }, Math.max(1L, (duration + 49L) / 50L));
        }
    }

    private String loopAnimation(Encounter encounter) {
        if (encounter.state == State.HUNTING || encounter.state == State.RETREATING) return "run";
        if (encounter.state == State.STALKING || encounter.state == State.SEARCHING) return locomotionAnimation(encounter);
        return "idle";
    }

    private String locomotionAnimation(Encounter encounter) {
        return encounter.type == EntityArchetype.CRAWLER
                || encounter.type == EntityArchetype.CRAWLING_ONE
                || encounter.type == EntityArchetype.CEILING_CRAWLER ? "crawl" : "stalk";
    }

    private void moveToward(ArmorStand shell, Location target, double maximumStep) {
        if (target == null || !target.getWorld().equals(shell.getWorld())) return;
        Vector direction = target.toVector().subtract(shell.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 4) return;
        direction.normalize().multiply(Math.min(maximumStep, Math.sqrt(direction.lengthSquared())));
        Location next = shell.getLocation().add(direction);
        next.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
        teleportIfLoaded(shell, next);
    }

    private void moveAway(ArmorStand shell, Location target, double step) {
        Vector direction = shell.getLocation().toVector().subtract(target.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < .01) direction = new Vector(1, 0, 0);
        direction.normalize().multiply(step);
        Location next = shell.getLocation().add(direction);
        next.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
        teleportIfLoaded(shell, next);
    }

    private void teleportIfLoaded(ArmorStand shell, Location destination) {
        if (destination.getWorld() == null) return;
        if (!destination.getWorld().isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) return;
        Block feet = destination.getBlock();
        Block head = destination.clone().add(0, 1, 0).getBlock();
        if (feet.isPassable() && head.isPassable()) shell.teleport(destination);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Encounter encounter : active.values()) {
            if (encounter.shell.isValid() && encounter.shell.getWorld().equals(event.getPlayer().getWorld())
                    && !encounter.target.equals(event.getPlayer().getUniqueId())) {
                event.getPlayer().hideEntity(plugin, encounter.shell);
            }
        }
    }

    private void remove(Encounter encounter) {
        if (encounter.modelTracker != null && !encounter.modelTracker.isClosed()) encounter.modelTracker.close();
        if (encounter.shell.isValid()) encounter.shell.remove();
    }

    private void clearAll() {
        for (Encounter encounter : active.values()) remove(encounter);
        active.clear();
    }
}
