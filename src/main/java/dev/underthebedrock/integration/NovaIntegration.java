package dev.underthebedrock.integration;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import xyz.xenondevs.nova.api.Nova;
import xyz.xenondevs.nova.api.block.NovaBlock;
import xyz.xenondevs.nova.api.item.NovaItem;
import java.util.Optional;

public final class NovaIntegration {
    private final UnderTheBedrockPlugin plugin;
    private Nova nova;
    private boolean enabled;
    public NovaIntegration(UnderTheBedrockPlugin plugin) { this.plugin = plugin; }

    public boolean initialize() {
        if (!plugin.getConfig().getBoolean("integrations.nova.enabled", true))
            return !plugin.getConfig().getBoolean("integrations.nova.required", true);
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("Nova");
        if (dependency == null || !dependency.isEnabled()) {
            plugin.getLogger().severe("Nova is missing or disabled."); return false;
        }
        try {
            nova = Nova.getNova();
            nova.getBlockRegistry(); nova.getItemRegistry(); nova.getBlockManager();
            enabled = true;
            plugin.getLogger().info("Nova API connected: " + dependency.getDescription().getVersion());
            return true;
        } catch (Throwable error) {
            plugin.getLogger().severe("Nova integration failed: " + error.getMessage()); return false;
        }
    }

    public Optional<NovaBlock> findBlock(String id) {
        return enabled && nova != null ? Optional.ofNullable(nova.getBlockRegistry().getOrNull(id)) : Optional.empty();
    }
    public Optional<NovaItem> findItem(String id) {
        return enabled && nova != null ? Optional.ofNullable(nova.getItemRegistry().getOrNull(id)) : Optional.empty();
    }
    public boolean placeBlock(String id, Location location) {
        Optional<NovaBlock> block = findBlock(id);
        if (block.isEmpty()) return false;
        nova.getBlockManager().placeBlock(location, block.get(), null, false);
        return true;
    }
    public String statusText() { return enabled ? "§aConnected" : "§cUnavailable"; }
}
