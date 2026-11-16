package dev.underthebedrock.horror;

import java.util.*;

public final class PlayerHorrorMemory {
    private double tension, provocation;
    private long lastOccurrenceAt, lastSevereOccurrenceAt, lastChatAt, lastEntityEncounterAt;
    private long encounters, distanceTravelled, lastClueAt, lastPhenomenonAt, phenomenonCount;
    private String lastOccurrence = "none", lastClueId = "none", lastPhenomenon = "none";
    private final Map<EntityArchetype, Integer> entityEncounters = new EnumMap<>(EntityArchetype.class);
    private final Set<String> discoveredClues = new LinkedHashSet<>(), discoveredLore = new LinkedHashSet<>();
    private final Set<String> heardRecordings = new LinkedHashSet<>(), visitedRegions = new LinkedHashSet<>();
    private final Set<String> phenomenaSeen = new LinkedHashSet<>();

    public double tension() { return tension; }
    public double provocation() { return provocation; }
    public long lastOccurrenceAt() { return lastOccurrenceAt; }
    public long lastSevereOccurrenceAt() { return lastSevereOccurrenceAt; }
    public long lastChatAt() { return lastChatAt; }
    public long lastEntityEncounterAt() { return lastEntityEncounterAt; }
    public long encounters() { return encounters; }
    public long distanceTravelled() { return distanceTravelled; }
    public String lastOccurrence() { return lastOccurrence; }
    public Map<EntityArchetype, Integer> entityEncounters() { return entityEncounters; }
    public Set<String> discoveredClues() { return Set.copyOf(discoveredClues); }
    public int discoveredClueCount() { return discoveredClues.size(); }
    public String lastClueId() { return lastClueId; }
    public long lastClueAt() { return lastClueAt; }
    public boolean hasClue(String id) { return discoveredClues.contains(id); }
    public Set<String> discoveredLore() { return Set.copyOf(discoveredLore); }
    public Set<String> heardRecordings() { return Set.copyOf(heardRecordings); }
    public boolean hasLore(String id) { return discoveredLore.contains(id); }
    public boolean hasRecording(String id) { return heardRecordings.contains(id); }
    public int loreCount() { return discoveredLore.size(); }
    public Set<String> visitedRegions() { return Set.copyOf(visitedRegions); }
    public Set<String> phenomenaSeen() { return Set.copyOf(phenomenaSeen); }
    public String lastPhenomenon() { return lastPhenomenon; }
    public long lastPhenomenonAt() { return lastPhenomenonAt; }
    public long phenomenonCount() { return phenomenonCount; }
    public WorldRecognitionStage recognitionStage() { return WorldRecognitionStage.calculate(this); }

    public boolean discoverLore(String id) { return id != null && !id.isBlank() && discoveredLore.add(id); }
    public boolean hearRecording(String id) { return id != null && !id.isBlank() && heardRecordings.add(id); }
    public boolean recordRegion(String id) { return id != null && !id.isBlank() && visitedRegions.add(id); }

    public void restore(double t, double p, long occurrence, long severe, long chat, long entityAt,
                        long count, long distance, String last, Map<EntityArchetype, Integer> entities,
                        Set<String> clues, String lastClue, long clueAt, Set<String> lore,
                        Set<String> recordings, Set<String> regions, Set<String> phenomena,
                        String lastPhenomenon, long lastPhenomenonAt, long phenomenonCount) {
        tension = clamp(t, 0, 100);
        provocation = clamp(p, 0, 100);
        lastOccurrenceAt = Math.max(0, occurrence);
        lastSevereOccurrenceAt = Math.max(0, severe);
        lastChatAt = Math.max(0, chat);
        lastEntityEncounterAt = Math.max(0, entityAt);
        encounters = Math.max(0, count);
        distanceTravelled = Math.max(0, distance);
        lastOccurrence = last == null ? "none" : last;
        entityEncounters.clear();
        if (entities != null) entityEncounters.putAll(entities);
        discoveredClues.clear();
        if (clues != null) discoveredClues.addAll(clues);
        lastClueId = lastClue == null ? "none" : lastClue;
        lastClueAt = Math.max(0, clueAt);
        discoveredLore.clear();
        if (lore != null) discoveredLore.addAll(lore);
        heardRecordings.clear();
        if (recordings != null) heardRecordings.addAll(recordings);
        visitedRegions.clear();
        if (regions != null) visitedRegions.addAll(regions);
        phenomenaSeen.clear();
        if (phenomena != null) phenomenaSeen.addAll(phenomena);
        this.lastPhenomenon = lastPhenomenon == null ? "none" : lastPhenomenon;
        this.lastPhenomenonAt = Math.max(0, lastPhenomenonAt);
        this.phenomenonCount = Math.max(0, phenomenonCount);
    }

    public void addTension(double n) { tension = clamp(tension + n, 0, 100); }
    public void decayTension(double n) { tension = clamp(tension - n, 0, 100); }
    public void provoke(double n) { provocation = clamp(provocation + n, 0, 100); }
    public void calmProvocation(double n) { provocation = clamp(provocation - n, 0, 100); }
    public void addDistance(long n) { distanceTravelled += Math.max(0, n); }
    public void recordChat(long n) { lastChatAt = n; }

    public void recordPhenomenon(String id, long now, double tensionGain) {
        if (id == null || id.isBlank()) return;
        lastPhenomenon = id;
        lastPhenomenonAt = Math.max(0, now);
        phenomenonCount++;
        phenomenaSeen.add(id);
        addTension(tensionGain);
    }

    public void recordOccurrence(String id, OccurrenceSeverity severity, long now) {
        lastOccurrence = id;
        lastOccurrenceAt = now;
        encounters++;
        if (severity.rank() >= OccurrenceSeverity.SCARY.rank()) lastSevereOccurrenceAt = now;
        addTension(severity.rank() * 1.8 + 1.5);
    }

    public void recordEntity(EntityArchetype type, long now) {
        entityEncounters.merge(type, 1, Integer::sum);
        lastEntityEncounterAt = now;
        addTension(3 + type.aggression() * 2);
    }

    public boolean discoverClue(String id, long now) {
        if (id == null || id.isBlank() || !discoveredClues.add(id)) return false;
        lastClueId = id;
        lastClueAt = now;
        addTension(4.5);
        return true;
    }

    public void relax() { decayTension(.08); calmProvocation(.025); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
