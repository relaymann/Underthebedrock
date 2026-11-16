package dev.underthebedrock.horror;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Distance and rarity gate for all entity encounters. */
public final class EntityEncounterPolicy {
    private final double safeRadius;
    private final double fullThreatDistance;
    private final double maximumChance;

    public EntityEncounterPolicy(double safeRadius, double fullThreatDistance, double maximumChance) {
        this.safeRadius = Math.max(0, safeRadius);
        this.fullThreatDistance = Math.max(this.safeRadius + 1, fullThreatDistance);
        this.maximumChance = Math.max(0, Math.min(1, maximumChance));
    }

    /** Zero inside the protected start zone; predictable linear growth as exploration deepens. */
    public double chanceAt(double distance) {
        if (!Double.isFinite(distance) || distance <= safeRadius) return 0;
        double progress = Math.min(1, (distance - safeRadius) / (fullThreatDistance - safeRadius));
        return maximumChance * progress;
    }

    public List<EntityArchetype> eligibleAt(double distance) {
        if (!Double.isFinite(distance) || distance <= safeRadius) return List.of();
        List<EntityArchetype> eligible = new ArrayList<>();
        for (EntityArchetype type : EntityArchetype.values()) {
            if (distance >= type.unlockDistance()) eligible.add(type);
        }
        return List.copyOf(eligible);
    }

    public Optional<EntityArchetype> roll(double distance, Random random) {
        List<EntityArchetype> eligible = eligibleAt(distance);
        if (eligible.isEmpty() || random.nextDouble() >= chanceAt(distance)) return Optional.empty();
        double total = eligible.stream().mapToDouble(type -> type.selectionWeight() * type.rarity().selectionMultiplier()).sum();
        double value = random.nextDouble() * total;
        for (EntityArchetype type : eligible) {
            value -= type.selectionWeight() * type.rarity().selectionMultiplier();
            if (value <= 0) return Optional.of(type);
        }
        return Optional.of(eligible.get(0));
    }

    /** Repeated sightings gently bias the director toward unfamiliar encounters. */
    public Optional<EntityArchetype> roll(double distance, Random random, PlayerHorrorMemory memory) {
        List<EntityArchetype> eligible = eligibleAt(distance);
        if (eligible.isEmpty() || random.nextDouble() >= chanceAt(distance)) return Optional.empty();
        double total = 0;
        for (EntityArchetype type : eligible) total += adaptiveWeight(type, memory);
        double value = random.nextDouble() * total;
        for (EntityArchetype type : eligible) {
            value -= adaptiveWeight(type, memory);
            if (value <= 0) return Optional.of(type);
        }
        return Optional.of(eligible.get(0));
    }

    private double adaptiveWeight(EntityArchetype type, PlayerHorrorMemory memory) {
        int sightings = memory == null ? 0 : memory.entityEncounters().getOrDefault(type, 0);
        return type.selectionWeight() * type.rarity().selectionMultiplier() / (1.0 + Math.min(8, sightings) * 0.18);
    }

    public double safeRadius() { return safeRadius; }
    public double fullThreatDistance() { return fullThreatDistance; }
    public double maximumChance() { return maximumChance; }
}
