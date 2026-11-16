package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.integration.BetterModelIntegration;
import dev.underthebedrock.world.VoidWorldManager;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
        private long lastAttackAt;
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
    private final HorrorSoundManager sounds;
    private final Map<UUID, Encounter> active = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;
    private BukkitTask movementTask;
    private boolean warned;

    public HorrorEntityController(UnderTheBedrockPlugin plugin, VoidWorldManager worlds,
                                  OccurrenceDirector director, BetterModelIntegration models) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.director = director;
        this.models = models;
        this.sounds = new HorrorSoundManager();
    }

    public void start() {
        if (task != null) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long ticks = Math.max(1, plugin.getConfig().getLong("horror.director.check-interval-seconds", 12)) * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
        movementTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::movementTick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (movementTask != null) movementTask.cancel();
        task = null;
        movementTask = null;
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
        Location spawn = findSpawn(player);
        if (spawn == null) return;
        var selected = director.trySelectEntityEncounter(player);
        selected.ifPresent(type -> createEncounter(player, type, spawn));
    }

    /** Manually creates a named apparition for an administrator command. */
    public boolean spawnFor(Player target, EntityArchetype type) {
        if (!worlds.isActive() || worlds.getWorld() == null
                || !target.getWorld().equals(worlds.getWorld()) || active.containsKey(target.getUniqueId())) {
            return false;
        }
        Location spawn = findManualSpawn(target);
        return spawn != null && createEncounter(target, type, spawn);
    }

    public boolean despawnFor(Player target) {
        Encounter encounter = active.remove(target.getUniqueId());
        if (encounter == null) return false;
        remove(encounter);
        return true;
    }

    public boolean hasEncounter(Player target) {
        return active.containsKey(target.getUniqueId());
    }

    private boolean createEncounter(Player player, EntityArchetype type, Location spawn) {
        Set<String> registered = models.availableModels();
        if (registered.isEmpty()) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("No BetterModel models registered; entity encounters paused.");
            }
            return false;
        }
        String key = type.name().toLowerCase(java.util.Locale.ROOT);
        String model = plugin.getConfig().getString("horror.entities.models." + key,
                plugin.getConfig().getString("integrations.bettermodel.default-watcher-model", "watcher"));
        if (!registered.contains(model)) {
            plugin.getLogger().warning("BetterModel model '" + model + "' is not registered for " + type + ".");
            return false;
        }

        ArmorStand shell = player.getWorld().spawn(spawn, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            // A marker stand has a zero-sized body. Keep a normal carrier so BetterModel
            // can track its entity position and viewer range reliably; the carrier stays hidden.
            stand.setMarker(false);
            stand.setSmall(false);
            stand.setBasePlate(false);
            stand.setArms(false);
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
            return false;
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) viewer.hideEntity(plugin, shell);
        }
        Encounter encounter = new Encounter(player.getUniqueId(), type, shell, player.getLocation(), tracker.get());
        active.put(player.getUniqueId(), encounter);
        plugin.getLogger().info("Created " + type.name().toLowerCase(java.util.Locale.ROOT)
                + " apparition model '" + model + "' for " + player.getName()
                + " at " + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ()
                + " (carrier " + shell.getUniqueId() + ").");
        setAnimation(encounter, "emerge");
        director.recordEntityEncounter(player, type);
        sounds.play(player, spawn, type, HorrorSoundManager.Cue.AMBIENT, .72f, .92f);
        return true;
    }

    /** Natural encounters appear far enough away to be discovered, but remain within loaded view distance. */
    private Location findSpawn(Player player) {
        return findSpawn(player, 22, 34, 95, 155);
    }

    /** Manual test spawns appear nearby and in front of the target for immediate verification. */
    private Location findManualSpawn(Player player) {
        return findSpawn(player, 7, 11, -32, 32);
    }

    private Location findSpawn(Player player, double minimumDistance, double maximumDistance,
                               double minimumAngle, double maximumAngle) {
        Location origin = player.getLocation();
        Vector forward = origin.getDirection().setY(0).normalize();
        if (forward.lengthSquared() < .01) forward = new Vector(0, 0, 1);
        int floor = plugin.getConfig().getInt("world.floor-y", 64);
        for (int i = 0; i < 32; i++) {
            double angle = Math.toRadians(minimumAngle + random.nextDouble() * (maximumAngle - minimumAngle));
            double distance = minimumDistance + random.nextDouble() * (maximumDistance - minimumDistance);
            Vector offset = forward.clone().rotateAroundY(angle).multiply(distance);
            int x = origin.getBlockX() + offset.getBlockX();
            int z = origin.getBlockZ() + offset.getBlockZ();
            if (!player.getWorld().isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            Location location = new Location(player.getWorld(), x + .5, floor + 1, z + .5);
            if (!location.getBlock().isPassable()
                    || !location.clone().add(0, 1, 0).getBlock().isPassable()
                    || !location.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            return location;
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
            case PEEKER, DOORFRAME, LURKER -> {
                if (visible) encounter.state = State.RETREATING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case CEILING_CRAWLER -> {
                int lightLimit = plugin.getConfig().getInt("horror.entities.behavior.crawler-maximum-light-level", 7);
                boolean dark = shell.getLocation().getBlock().getLightLevel() <= lightLimit;
                if (dark && distanceSquared < 400 && !visible) encounter.state = State.HUNTING;
                else if (visible) encounter.state = State.RETREATING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case CRAWLER -> {
                int lightLimit = plugin.getConfig().getInt("horror.entities.behavior.crawler-maximum-light-level", 7);
                boolean dark = shell.getLocation().getBlock().getLightLevel() <= lightLimit;
                if (dark && distanceSquared < 3600) encounter.state = State.HUNTING;
                else if (visible) encounter.state = State.RETREATING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.STALKING;
            }
            case PURSUER, AGGRESSOR, CRAWLING_ONE, HEAVY, EXCAVATOR -> {
                double provocation = director.memory(player.getUniqueId()).provocation();
                if (provocation >= 12 || distanceSquared < 1600) encounter.state = State.HUNTING;
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
                        sounds.play(player, shell.getLocation(), encounter.type, HorrorSoundManager.Cue.AMBIENT, .22f, .72f + random.nextFloat() * .2f);
                    } else if (random.nextDouble() < 0.10) {
                        sounds.play(player, shell.getLocation(), encounter.type, HorrorSoundManager.Cue.ALERT, .18f, .78f);
                    }
                }
            }
            case IMITATOR, DOUBLE -> {
                if (visible) encounter.state = State.OBSERVING;
                else if (encounter.unseenChecks >= 2) encounter.state = State.SEARCHING;
            }
            case ECHO -> {
                if (!visible && encounter.unseenChecks % 2 == 0) sounds.play(player, player.getLocation(), encounter.type, HorrorSoundManager.Cue.AMBIENT, .18f, .68f);
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
            sounds.play(player, shell.getLocation(), encounter.type, HorrorSoundManager.Cue.ALERT, .85f, .82f);
        }

        if (encounter.state == State.DISAPPEARING) {
            if (!encounter.disappearanceStarted) {
                encounter.disappearanceStarted = true;
                setAnimation(encounter, "disappear");
                sounds.play(player, shell.getLocation(), encounter.type, HorrorSoundManager.Cue.VANISH, .8f, .9f);
                shell.getWorld().spawnParticle(Particle.SMOKE, shell.getLocation().add(0, 1, 0), 18, .25, .5, .25, .025);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    remove(encounter);
                    active.remove(encounter.target, encounter);
                }, 25L);
            }
            return;
        }
        if (encounter.state == State.STALKING && encounter.type == EntityArchetype.DOORFRAME
                && encounter.unseenChecks == 2) {
            Location behind = player.getLocation().subtract(
                    player.getLocation().getDirection().setY(0).normalize().multiply(12));
            behind.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
            teleportIfLoaded(shell, behind);
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
    }

    /**
     * Moves existing apparitions every server tick. Encounter discovery and state decisions stay
     * on the slower director cadence, while bounded movement and attacks remain responsive.
     */
    private void movementTick() {
        if (!worlds.isActive()) return;
        World world = worlds.getWorld();
        if (world == null) return;

        var iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Encounter encounter = iterator.next().getValue();
            Player player = plugin.getServer().getPlayer(encounter.target);
            ArmorStand shell = encounter.shell;
            if (player == null || !player.getWorld().equals(world) || !shell.isValid()
                    || !shell.getWorld().equals(world)) {
                remove(encounter);
                iterator.remove();
                continue;
            }
            if (encounter.state == State.DISAPPEARING) continue;

            double speed = movementSpeed(encounter);
            if (encounter.state == State.HUNTING) {
                encounter.lastKnown = player.getLocation().clone();
                double distanceSquared = shell.getLocation().distanceSquared(player.getLocation());
                if (canAttack(encounter.type) && distanceSquared <= attackRange(encounter.type) * attackRange(encounter.type)
                        && encounter.animationUntil <= System.currentTimeMillis()
                        && System.currentTimeMillis() - encounter.lastAttackAt >= attackCooldown(encounter.type)) {
                    encounter.lastAttackAt = System.currentTimeMillis();
                    setAnimation(encounter, "attack");
                    player.damage(attackDamage(encounter.type), shell);
                    Vector knockback = player.getLocation().toVector().subtract(shell.getLocation().toVector());
                    if (knockback.lengthSquared() > .01) {
                        knockback.normalize().multiply(encounter.type == EntityArchetype.HEAVY ? .9 : .62).setY(.24);
                        player.setVelocity(knockback);
                    }
                    sounds.play(player, shell.getLocation(), encounter.type, HorrorSoundManager.Cue.ATTACK, .95f, .88f);
                    continue;
                }
                moveToward(shell, player.getLocation(), speed);
                continue;
            }
            if (encounter.state == State.RETREATING) {
                double retreatSpeed = speed * (encounter.type == EntityArchetype.CEILING_CRAWLER ? 1.8 : 1.5);
                moveAway(shell, player.getLocation(), retreatSpeed);
                if (shell.getLocation().distanceSquared(player.getLocation()) > 10000.0) {
                    remove(encounter);
                    iterator.remove();
                }
                continue;
            }
            if (encounter.state == State.STALKING) {
                moveToward(shell, encounter.lastKnown, speed);
                continue;
            }
            if (encounter.state == State.SEARCHING && !player.hasLineOfSight(shell)) {
                moveToward(shell, encounter.lastKnown, speed * .72);
                continue;
            }

            if (encounter.type == EntityArchetype.WANDERER || encounter.type == EntityArchetype.DRIFTER) {
                double directorTicks = Math.max(1L,
                        plugin.getConfig().getLong("horror.director.check-interval-seconds", 12)) * 20.0;
                double chancePerTick = (encounter.type == EntityArchetype.DRIFTER ? .18 : .12) / directorTicks;
                if (random.nextDouble() < chancePerTick) {
                    Location roam = shell.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
                    roam.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
                    teleportIfLoaded(shell, roam);
                }
            }
        }
    }

    private boolean canAttack(EntityArchetype type) {
        return type.isPursuer() || type == EntityArchetype.DEEP_ENTITY
                || type == EntityArchetype.MIMIC || type == EntityArchetype.CEILING_CRAWLER;
    }

    private double attackRange(EntityArchetype type) {
        return switch (type) {
            case HEAVY, DEEP_ENTITY, EXCAVATOR -> 2.8;
            case CRAWLING_ONE, CRAWLER, CEILING_CRAWLER -> 2.2;
            default -> 2.0;
        };
    }

    private double attackDamage(EntityArchetype type) {
        return switch (type) {
            case HEAVY, DEEP_ENTITY -> 7.0;
            case EXCAVATOR, AGGRESSOR -> 5.0;
            case MIMIC -> 4.0;
            case PURSUER -> 3.5;
            default -> 2.5;
        };
    }

    private long attackCooldown(EntityArchetype type) {
        return switch (type) {
            case CRAWLING_ONE, CRAWLER -> 1800L;
            case HEAVY, DEEP_ENTITY, EXCAVATOR -> 4200L;
            case MIMIC -> 3000L;
            default -> 2600L;
        };
    }

    private double movementSpeed(Encounter encounter) {
        double base = plugin.getConfig().getDouble(
                "horror.entities.behavior.movement-speed-blocks-per-tick", .085);
        base = Math.max(.01, Math.min(.20, base));
        return switch (encounter.type) {
            case HEAVY, DEEP_ENTITY -> base * .48;
            case CRAWLING_ONE -> base * 1.65;
            case CRAWLER -> base * 1.35;
            case MIMIC -> base * 1.5;
            case CEILING_CRAWLER -> base * 1.25;
            case DRIFTER -> base * .55;
            default -> base;
        };
    }

    private float approachYaw(float current, float target) {
        float difference = (target - current + 540.0f) % 360.0f - 180.0f;
        return current + Math.max(-8.0f, Math.min(8.0f, difference));
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
            sounds.play(player, at, encounter.type, HorrorSoundManager.Cue.ALERT, 1.2f, .62f);
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
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        direction.normalize().multiply(Math.min(maximumStep, Math.sqrt(direction.lengthSquared())));
        Location current = shell.getLocation();
        Location next = current.add(direction);
        next.setYaw(approachYaw(current.getYaw(), targetYaw));
        next.setY(plugin.getConfig().getInt("world.floor-y", 64) + 1);
        teleportIfLoaded(shell, next);
    }

    private void moveAway(ArmorStand shell, Location target, double step) {
        Vector direction = shell.getLocation().toVector().subtract(target.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < .01) direction = new Vector(1, 0, 0);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        direction.normalize().multiply(step);
        Location current = shell.getLocation();
        Location next = current.add(direction);
        next.setYaw(approachYaw(current.getYaw(), targetYaw));
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
        hideForeignEncounters(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // A player can enter the horror world after an encounter already exists.
        // Re-apply per-player hiding after every world transition, not only at login.
        hideForeignEncounters(event.getPlayer());
    }

    private void hideForeignEncounters(Player viewer) {
        for (Encounter encounter : active.values()) {
            if (encounter.shell.isValid()
                    && encounter.shell.getWorld().equals(viewer.getWorld())
                    && !encounter.target.equals(viewer.getUniqueId())) {
                viewer.hideEntity(plugin, encounter.shell);
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
