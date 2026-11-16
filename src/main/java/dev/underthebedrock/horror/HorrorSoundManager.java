package dev.underthebedrock.horror;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Plays the resource-pack-backed Under The Bedrock sound events.
 * The resource pack is merged into BetterModel's generated pack before distribution.
 */
public final class HorrorSoundManager {
    public enum Cue { AMBIENT, ALERT, ATTACK, VANISH }

    public void play(Player player, Location location, EntityArchetype type, Cue cue, float volume, float pitch) {
        String entityId = type.name().toLowerCase(java.util.Locale.ROOT);
        String soundId = "underthebedrock:entity." + entityId + "." + cue.name().toLowerCase(java.util.Locale.ROOT);
        player.playSound(location, soundId, volume, pitch);
    }
}
