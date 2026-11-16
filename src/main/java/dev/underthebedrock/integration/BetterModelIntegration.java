package dev.underthebedrock.integration;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.horror.EntityArchetype;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class BetterModelIntegration {
    private static final List<String> BUNDLED_MODELS = List.of(
            "absence", "aggressor", "ceiling_crawler", "crawler", "crawling_one",
            "deep_entity", "doorframe", "double", "drifter", "echo", "excavator",
            "forgotten_resident", "heavy", "imitator", "lurker", "mimic",
            "mimic_revealed", "misprint", "observer", "original", "peeker",
            "pursuer", "stalker", "surveyor", "unperson", "wanderer", "watcher", "witness"
    );

    private final UnderTheBedrockPlugin plugin;
    private boolean enabled;

    public BetterModelIntegration(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        if (!plugin.getConfig().getBoolean("integrations.bettermodel.enabled", true))
            return !plugin.getConfig().getBoolean("integrations.bettermodel.required", true);

        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("BetterModel");
        if (dependency == null || !dependency.isEnabled()) {
            plugin.getLogger().severe("BetterModel is missing or disabled. Install the BetterModel plugin once; Under The Bedrock will install and configure all of its own models automatically.");
            return false;
        }

        try {
            File betterModelFolder = dependency.getDataFolder();
            File modelsFolder = new File(betterModelFolder, "models");
            if (!modelsFolder.isDirectory() && !modelsFolder.mkdirs())
                throw new IOException("Could not create " + modelsFolder);

            int copied = provisionBundledModels(modelsFolder);
            boolean configChanged = configureBetterModel(betterModelFolder);
            boolean pluginConfigChanged = migrateModelIds();
            if (pluginConfigChanged) plugin.saveConfig();
            if (copied > 0 || configChanged) {
                plugin.getLogger().info("Installed or updated " + copied + " bundled Under The Bedrock models and applied BetterModel compatibility settings.");
                BetterModel.platform().reload();
            }

            Set<String> keys = BetterModel.modelKeys();
            enabled = true;
            for (EntityArchetype type : EntityArchetype.values()) {
                String id = type.name().toLowerCase(Locale.ROOT);
                String configured = plugin.getConfig().getString("horror.entities.models." + id, "utb_" + id);
                if (!keys.contains(configured))
                    plugin.getLogger().severe("Required Under The Bedrock model '" + configured + "' is not loaded by BetterModel.");
            }
            String apostleModel = plugin.getConfig().getString("horror.enclosure.apostle-model", "utb_unperson");
            if (!keys.contains(apostleModel))
                plugin.getLogger().severe("Required Apostle model '" + apostleModel + "' is not loaded by BetterModel.");
            plugin.getLogger().info("BetterModel connected: " + dependency.getDescription().getVersion()
                    + "; " + copied + " bundled models provisioned; " + keys.size() + " models available.");
            return true;
        } catch (Throwable error) {
            plugin.getLogger().severe("BetterModel automatic setup failed: " + error.getMessage());
            error.printStackTrace();
            return false;
        }
    }

    private int provisionBundledModels(File modelsFolder) throws IOException {
        int copied = 0;
        for (String model : BUNDLED_MODELS) {
            String resource = "under-the-bedrock/models/" + model + ".bbmodel";
            File target = new File(modelsFolder, "utb_" + model + ".bbmodel");
            try (InputStream input = plugin.getResource(resource)) {
                if (input == null) throw new IOException("Bundled model resource is missing: " + resource);
                byte[] bundled = input.readAllBytes();
                if (!target.isFile() || !java.util.Arrays.equals(bundled, Files.readAllBytes(target.toPath()))) {
                    Files.write(target.toPath(), bundled);
                    copied++;
                }
            }
        }
        return copied;
    }

    private boolean migrateModelIds() {
        boolean changed = false;
        for (EntityArchetype type : EntityArchetype.values()) {
            String id = type.name().toLowerCase(Locale.ROOT);
            String path = "horror.entities.models." + id;
            if (plugin.getConfig().getString(path, id).equals(id)) {
                plugin.getConfig().set(path, "utb_" + id);
                changed = true;
            }
        }
        if (plugin.getConfig().getString("horror.enclosure.apostle-model", "unperson").equals("unperson")) {
            plugin.getConfig().set("horror.enclosure.apostle-model", "utb_unperson");
            changed = true;
        }
        if (plugin.getConfig().getString("integrations.bettermodel.default-watcher-model", "watcher").equals("watcher")) {
            plugin.getConfig().set("integrations.bettermodel.default-watcher-model", "utb_watcher");
            changed = true;
        }
        return changed;
    }

    private boolean configureBetterModel(File folder) throws IOException {
        File configFile = new File(folder, "config.yml");
        if (!configFile.isFile()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean changed = config.getBoolean("follow-mob-invisibility", true);
        if (changed) {
            config.set("follow-mob-invisibility", false);
            config.save(configFile);
        }
        return changed;
    }

    public Optional<EntityTracker> attachModel(Entity entity, String modelId) {
        if (!enabled) return Optional.empty();
        return BetterModel.model(modelId).map(renderer -> renderer.create(BukkitAdapter.adapt(entity)));
    }

    public Optional<EntityTracker> replaceModel(Entity entity, EntityTracker current, String modelId) {
        Optional<EntityTracker> replacement = attachModel(entity, modelId);
        replacement.ifPresent(next -> {
            if (current != null && !current.isClosed()) current.close();
        });
        return replacement;
    }

    public boolean playAnimation(EntityTracker tracker, String animation) {
        if (!enabled || tracker == null || tracker.isClosed()) return false;
        try {
            return tracker.animate(animation);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not play BetterModel animation '" + animation + "': " + error.getMessage());
            return false;
        }
    }

    public Set<String> availableModels() {
        return enabled ? BetterModel.modelKeys() : Set.of();
    }

    public String statusText() {
        return enabled ? "§aConnected (" + availableModels().size() + " models)" : "§cUnavailable";
    }
}
