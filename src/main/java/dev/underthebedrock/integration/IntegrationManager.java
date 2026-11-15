package dev.underthebedrock.integration;

import dev.underthebedrock.UnderTheBedrockPlugin;

/**
 * Owns the visual integration used to render horror apparitions.
 * Core world, survival, evidence, and horror-event systems use Paper directly.
 */
public final class IntegrationManager {
    private final UnderTheBedrockPlugin plugin;
    private final BetterModelIntegration betterModel;

    public IntegrationManager(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        this.betterModel = new BetterModelIntegration(plugin);
    }

    public boolean initialize() {
        boolean available = betterModel.initialize();
        if (plugin.getConfig().getBoolean("integrations.bettermodel.required", true) && !available) {
            plugin.getLogger().severe("Required integration unavailable. Install/enable BetterModel, then restart.");
            return false;
        }
        return true;
    }

    public BetterModelIntegration betterModel() {
        return betterModel;
    }
}
