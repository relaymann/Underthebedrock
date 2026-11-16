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
import java.util.Optional;
import java.util.Set;

public final class BetterModelIntegration {
    private final UnderTheBedrockPlugin plugin;
    private boolean enabled;
    public BetterModelIntegration(UnderTheBedrockPlugin plugin) { this.plugin = plugin; }

    public boolean initialize() {
        if (!plugin.getConfig().getBoolean("integrations.bettermodel.enabled", true))
            return !plugin.getConfig().getBoolean("integrations.bettermodel.required", true);
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("BetterModel");
        if (dependency == null || !dependency.isEnabled()) {
            plugin.getLogger().severe("BetterModel is missing or disabled."); return false;
        }
        try {
            Set<String> keys = BetterModel.modelKeys();
            enabled = true;
            warnAboutCarrierVisibility();
            for (EntityArchetype type : EntityArchetype.values()) {
                String id = type.name().toLowerCase(java.util.Locale.ROOT);
                String configured = plugin.getConfig().getString("horror.entities.models." + id, id);
                if (!keys.contains(configured)) plugin.getLogger().warning("Missing BetterModel model '" + configured + "' for entity " + id + ". Copy its .bbmodel into plugins/BetterModel/models and run /bettermodel reload.");
            }
            plugin.getLogger().info("BetterModel API connected: " + dependency.getDescription().getVersion());
            return true;
        } catch (Throwable error) {
            plugin.getLogger().severe("BetterModel integration failed: " + error.getMessage()); return false;
        }
    }

    private void warnAboutCarrierVisibility() {
        File configFile = new File(plugin.getDataFolder().getParentFile(), "BetterModel/config.yml");
        if (!configFile.isFile()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (config.getBoolean("follow-mob-invisibility", true)) {
            plugin.getLogger().severe("BetterModel follow-mob-invisibility is enabled. Under The Bedrock uses invisible Armor Stand carriers, so BetterModel will hide the apparition models. Set follow-mob-invisibility: false in plugins/BetterModel/config.yml, then restart or reload BetterModel.");
        }
    }

    /** The model ID must exist in plugins/BetterModel/models before attachment. */
    public Optional<EntityTracker> attachModel(Entity entity, String modelId) {
        if (!enabled) return Optional.empty();
        return BetterModel.model(modelId).map(renderer -> renderer.create(BukkitAdapter.adapt(entity)));
    }
    /** Attach the replacement before closing the current tracker to avoid a blank frame if lookup fails. */
    public Optional<EntityTracker> replaceModel(Entity entity, EntityTracker current, String modelId) {
        Optional<EntityTracker> replacement = attachModel(entity, modelId);
        replacement.ifPresent(next -> {
            if (current != null && !current.isClosed()) current.close();
        });
        return replacement;
    }

    public boolean playAnimation(EntityTracker tracker, String animation) {
        if (!enabled || tracker == null || tracker.isClosed()) return false;
        try { return tracker.animate(animation); }
        catch (RuntimeException error) {
            plugin.getLogger().warning("Could not play BetterModel animation '" + animation + "': " + error.getMessage());
            return false;
        }
    }

    public Set<String> availableModels() { return enabled ? BetterModel.modelKeys() : Set.of(); }
    public String statusText() { return enabled ? "§aConnected (" + availableModels().size() + " models)" : "§cUnavailable"; }
}
