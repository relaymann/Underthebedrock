package dev.underthebedrock.integration;

import dev.underthebedrock.UnderTheBedrockPlugin;

public final class IntegrationManager {
    private final UnderTheBedrockPlugin plugin;
    private final NovaIntegration nova;
    private final BetterModelIntegration betterModel;

    public IntegrationManager(UnderTheBedrockPlugin plugin) {
        this.plugin = plugin;
        nova = new NovaIntegration(plugin);
        betterModel = new BetterModelIntegration(plugin);
    }

    public boolean initialize() {
        boolean n = nova.initialize();
        boolean b = betterModel.initialize();
        if ((plugin.getConfig().getBoolean("integrations.nova.required", true) && !n)
            || (plugin.getConfig().getBoolean("integrations.bettermodel.required", true) && !b)) {
            plugin.getLogger().severe("Required integrations unavailable. Install/enable Nova and BetterModel, then restart.");
            return false;
        }
        return true;
    }
    public NovaIntegration nova() { return nova; }
    public BetterModelIntegration betterModel() { return betterModel; }
}
