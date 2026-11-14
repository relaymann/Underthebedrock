package dev.underthebedrock.horror;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Distance gate used by model-backed entity controllers before selecting an archetype. */
public final class EntityEncounterPolicy {
    private final double safeRadius;
    private final double fullThreatDistance;
    private final double maximumChance;

    public EntityEncounterPolicy(double safeRadius, double fullThreatDistance, double maximumChance) {
        this.safeRadius = Math.max(0, safeRadius);
        this.fullThreatDistance = Math.max(this.safeRadius + 1, fullThreatDistance);
        this.maximumChance = Math.max(0, Math.min(1, maximumChance));
    }

    /** Zero inside the protected start zone; quadratic growth beyond it. */
    public double chanceAt(double distance) {
        if (!Double.isFinite(distance) || distance <= safeRadius) return 0;
        double progress = Math.min(1, (distance - safeRadius) / (fullThreatDistance - safeRadius));
        return maximumChance * progress * progress;
    }

    public List<EntityArchetype> eligibleAt(double distance) {
        List<EntityArchetype> eligible = new ArrayList<>();
        if (distance >= 768) eligible.add(EntityArchetype.OBSERVER);
        if (distance >= 1024) eligible.add(EntityArchetype.STALKER);
        if (distance >= 1536) eligible.add(EntityArchetype.WANDERER);
        if (distance >= 2048) eligible.add(EntityArchetype.MIMIC);
        if (distance >= 3072) eligible.add(EntityArchetype.AGGRESSOR);
        if (distance >= 6144) eligible.add(EntityArchetype.DEEP_ENTITY);
        return List.copyOf(eligible);
    }

    public Optional<EntityArchetype> roll(double distance, Random random) {
        if (random.nextDouble() >= chanceAt(distance)) return Optional.empty();
        List<EntityArchetype> eligible = eligibleAt(distance);
        if (eligible.isEmpty()) return Optional.empty();
        double total = 0;
        for (EntityArchetype type : eligible) total += selectionWeight(type);
        double value = random.nextDouble() * total;
        for (EntityArchetype type : eligible) {
            value -= selectionWeight(type);
            if (value <= 0) return Optional.of(type);
        }
        return Optional.of(eligible.get(0));
    }

    private double selectionWeight(EntityArchetype type) {
        return switch (type) {
            case OBSERVER -> 4.0;
            case STALKER -> 3.5;
            case WANDERER -> 2.5;
            case MIMIC -> 1.8;
            case AGGRESSOR -> 0.8;
            case DEEP_ENTITY -> 0.12;
        };
    }

    public double safeRadius() { return safeRadius; }
    public double fullThreatDistance() { return fullThreatDistance; }
    public double maximumChance() { return maximumChance; }
}
