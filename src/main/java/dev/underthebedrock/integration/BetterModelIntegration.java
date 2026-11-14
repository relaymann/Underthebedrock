package dev.underthebedrock.integration;

import dev.underthebedrock.UnderTheBedrockPlugin;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
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
            BetterModel.modelKeys();
            enabled = true;
            plugin.getLogger().info("BetterModel API connected: " + dependency.getDescription().getVersion());
            return true;
        } catch (Throwable error) {
            plugin.getLogger().severe("BetterModel integration failed: " + error.getMessage()); return false;
        }
    }

    /** The model ID must exist in plugins/BetterModel/models before attachment. */
    public Optional<EntityTracker> attachModel(Entity entity, String modelId) {
        if (!enabled) return Optional.empty();
        return BetterModel.model(modelId).map(renderer -> renderer.create(BukkitAdapter.adapt(entity)));
    }
    public Set<String> availableModels() { return enabled ? BetterModel.modelKeys() : Set.of(); }
    public String statusText() { return enabled ? "§aConnected (" + availableModels().size() + " models)" : "§cUnavailable"; }
}
