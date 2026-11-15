package dev.underthebedrock.horror;

import java.util.*;

public final class PlayerHorrorMemory {
    private double tension, provocation;
    private long lastOccurrenceAt, lastSevereOccurrenceAt, lastChatAt, lastEntityEncounterAt;
    private long encounters, distanceTravelled, lastClueAt;
    private String lastOccurrence = "none", lastClueId = "none";
    private final Map<EntityArchetype, Integer> entityEncounters = new EnumMap<>(EntityArchetype.class);
    private final Map<String, Integer> behaviorCounts = new LinkedHashMap<>();
    private final Set<String> discoveredClues = new LinkedHashSet<>();
    private final Set<String> discoveredLore = new LinkedHashSet<>();
    private final Set<String> heardRecordings = new LinkedHashSet<>();

    public double tension() { return tension; }
    public double provocation() { return provocation; }
    public long lastOccurrenceAt() { return lastOccurrenceAt; }
    public long lastSevereOccurrenceAt() { return lastSevereOccurrenceAt; }
    public long lastChatAt() { return lastChatAt; }
    public long lastEntityEncounterAt() { return lastEntityEncounterAt; }
    public long encounters() { return encounters; }
    public long distanceTravelled() { return distanceTravelled; }
    public String lastOccurrence() { return lastOccurrence; }
    public Map<EntityArchetype, Integer> entityEncounters() { return Collections.unmodifiableMap(entityEncounters); }
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
    public Map<String, Integer> behaviorCounts() { return Collections.unmodifiableMap(behaviorCounts); }
    public int behaviorCount(String id) { return behaviorCounts.getOrDefault(id, 0); }
    public boolean hasBehavior(String id) { return behaviorCount(id) > 0; }

    public boolean discoverLore(String id) {
        return id != null && !id.isBlank() && discoveredLore.add(id);
    }

    public boolean hearRecording(String id) {
        return id != null && !id.isBlank() && heardRecordings.add(id);
    }

    public int recordBehavior(String id) {
        if (id == null || !id.matches("[a-z0-9_]{1,48}")) return 0;
        int next = behaviorCounts.getOrDefault(id, 0) + 1;
        behaviorCounts.put(id, next);
        return next;
    }

    /** Backward-compatible restore path for saves/tests created before behavior memory existed. */
    public void restore(double t, double p, long occurrence, long severe, long chat, long entityAt,
                        long count, long distance, String last, Map<EntityArchetype, Integer> entities,
                        Set<String> clues, String lastClue, long clueAt, Set<String> lore,
                        Set<String> recordings) {
        restore(t, p, occurrence, severe, chat, entityAt, count, distance, last, entities,
            clues, lastClue, clueAt, lore, recordings, Map.of());
    }

    public void restore(double t, double p, long occurrence, long severe, long chat, long entityAt,
                        long count, long distance, String last, Map<EntityArchetype, Integer> entities,
                        Set<String> clues, String lastClue, long clueAt, Set<String> lore,
                        Set<String> recordings, Map<String, Integer> behaviors) {
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
        behaviorCounts.clear();
        if (behaviors != null) behaviors.forEach((key, value) -> {
            if (key != null && key.matches("[a-z0-9_]{1,48}") && value != null && value > 0) {
                behaviorCounts.put(key, value);
            }
        });
    }

    public void addTension(double amount) { tension = clamp(tension + amount, 0, 100); }
    public void decayTension(double amount) { tension = clamp(tension - amount, 0, 100); }
    public void provoke(double amount) { provocation = clamp(provocation + amount, 0, 100); }
    public void calmProvocation(double amount) { provocation = clamp(provocation - amount, 0, 100); }
    public void addDistance(long amount) { distanceTravelled += Math.max(0, amount); }
    public void recordChat(long time) { lastChatAt = time; }

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

    public void relax() {
        decayTension(.08);
        calmProvocation(.025);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
