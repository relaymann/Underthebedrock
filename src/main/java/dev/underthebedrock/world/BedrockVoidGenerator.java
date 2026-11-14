package dev.underthebedrock.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.Random;

/** Generates a true void; every requested chunk receives one bedrock floor layer. */
public final class BedrockVoidGenerator extends ChunkGenerator {
    private final int floorY;
    public BedrockVoidGenerator(int floorY) { this.floorY = floorY; }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (floorY < data.getMinHeight() || floorY >= data.getMaxHeight()) return;
        data.setRegion(0, floorY, 0, 16, floorY + 1, 16, Material.BEDROCK);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
