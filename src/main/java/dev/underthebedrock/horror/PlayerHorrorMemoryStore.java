package dev.underthebedrock.horror;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PlayerHorrorMemoryStore {
    private final UnderTheBedrockPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public PlayerHorrorMemoryStore(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "player-horror-memory.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    public PlayerHorrorMemory load(UUID id) {
        String root = id.toString();
        ConfigurationSection section = data.getConfigurationSection(root);
        PlayerHorrorMemory memory = new PlayerHorrorMemory();
        if (section == null) return memory;

        Map<EntityArchetype, Integer> entityCounts = new EnumMap<>(EntityArchetype.class);
        ConfigurationSection entities = section.getConfigurationSection("entities");
        if (entities != null) {
            for (EntityArchetype archetype : EntityArchetype.values()) {
                int count = Math.max(0, entities.getInt(archetype.name()));
                if (count > 0) entityCounts.put(archetype, count);
            }
        }

        Map<String, Integer> behaviors = new LinkedHashMap<>();
        ConfigurationSection behaviorSection = section.getConfigurationSection("behaviors");
        if (behaviorSection != null) {
            for (String key : behaviorSection.getKeys(false)) {
                int count = Math.max(0, behaviorSection.getInt(key));
                if (count > 0 && key.matches("[a-z0-9_]{1,48}")) behaviors.put(key, count);
            }
        }

        memory.restore(
            section.getDouble("tension"), section.getDouble("provocation"),
            section.getLong("last-occurrence-at"), section.getLong("last-severe-occurrence-at"),
            section.getLong("last-chat"), section.getLong("last-entity-encounter-at"),
            section.getLong("encounters"), section.getLong("distance-travelled"),
            section.getString("last-occurrence", "none"), entityCounts,
            new LinkedHashSet<>(section.getStringList("discovered-clues")),
            section.getString("last-clue-id", "none"), section.getLong("last-clue-at"),
            new LinkedHashSet<>(section.getStringList("discovered-lore")),
            new LinkedHashSet<>(section.getStringList("heard-recordings")), behaviors
        );
        return memory;
    }

    public void save(UUID id, PlayerHorrorMemory memory) {
        String root = id.toString();
        data.set(root + ".tension", memory.tension());
        data.set(root + ".provocation", memory.provocation());
        data.set(root + ".last-occurrence", memory.lastOccurrence());
        data.set(root + ".last-occurrence-at", memory.lastOccurrenceAt());
        data.set(root + ".last-severe-occurrence-at", memory.lastSevereOccurrenceAt());
        data.set(root + ".last-chat", memory.lastChatAt());
        data.set(root + ".last-entity-encounter-at", memory.lastEntityEncounterAt());
        data.set(root + ".encounters", memory.encounters());
        data.set(root + ".distance-travelled", memory.distanceTravelled());
        data.set(root + ".discovered-clues", memory.discoveredClues().stream().sorted().toList());
        data.set(root + ".last-clue-id", memory.lastClueId());
        data.set(root + ".last-clue-at", memory.lastClueAt());
        data.set(root + ".discovered-lore", memory.discoveredLore().stream().sorted().toList());
        data.set(root + ".heard-recordings", memory.heardRecordings().stream().sorted().toList());

        for (EntityArchetype archetype : EntityArchetype.values()) {
            data.set(root + ".entities." + archetype.name(),
                memory.entityEncounters().getOrDefault(archetype, 0));
        }
        for (Map.Entry<String, Integer> behavior : memory.behaviorCounts().entrySet()) {
            data.set(root + ".behaviors." + behavior.getKey(), behavior.getValue());
        }

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create horror memory directory.");
                return;
            }
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save horror memory: " + exception.getMessage());
        }
    }
}
