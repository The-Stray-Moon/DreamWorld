package cc.sighs.dreamworld.config;

import cc.sighs.dreamworld.spi.DreamConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric has no built-in config system, so the same six values are stored in one JSON file.
 *
 * <p>The server owns the world-affecting values; the client values only affect drawing. A single
 * file keeps the two sides in step without pulling in a second config library.</p>
 */
public final class FabricConfig implements DreamConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "dreamworld.json";

    private boolean swapPalettes = false;
    private int ambientSkyLight = 12;
    private int maxTunnelsPerGraffiti = 5;
    private int dreamEntryDelayTicks = 60;
    private double graffitiSleepRange = 16.0D;
    private double maxGraffitiSleepSpeedup = 0.6D;

    public void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            save(path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return;
            }
            swapPalettes = readBoolean(json, "swapPalettes", swapPalettes);
            ambientSkyLight = readInt(json, "ambientSkyLight", ambientSkyLight);
            maxTunnelsPerGraffiti = readInt(json, "maxTunnelsPerGraffiti", maxTunnelsPerGraffiti);
            dreamEntryDelayTicks = readInt(json, "dreamEntryDelayTicks", dreamEntryDelayTicks);
            graffitiSleepRange = readDouble(json, "graffitiSleepRange", graffitiSleepRange);
            maxGraffitiSleepSpeedup = readDouble(json, "maxGraffitiSleepSpeedup",
                    maxGraffitiSleepSpeedup);
        } catch (IOException | RuntimeException ignored) {
            // A broken file falls back to the defaults; the next launch rewrites it.
        }
    }

    private void save(Path path) {
        JsonObject json = new JsonObject();
        json.addProperty("swapPalettes", swapPalettes);
        json.addProperty("ambientSkyLight", ambientSkyLight);
        json.addProperty("maxTunnelsPerGraffiti", maxTunnelsPerGraffiti);
        json.addProperty("dreamEntryDelayTicks", dreamEntryDelayTicks);
        json.addProperty("graffitiSleepRange", graffitiSleepRange);
        json.addProperty("maxGraffitiSleepSpeedup", maxGraffitiSleepSpeedup);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException ignored) {
            // A config that cannot be written keeps the in-memory defaults.
        }
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static int readInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static double readDouble(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    @Override
    public boolean swapPalettes() {
        return swapPalettes;
    }

    @Override
    public int ambientSkyLight() {
        return ambientSkyLight;
    }

    @Override
    public int maxTunnelsPerGraffiti() {
        return maxTunnelsPerGraffiti;
    }

    @Override
    public int dreamEntryDelayTicks() {
        return dreamEntryDelayTicks;
    }

    @Override
    public double graffitiSleepRange() {
        return graffitiSleepRange;
    }

    @Override
    public double maxGraffitiSleepSpeedup() {
        return maxGraffitiSleepSpeedup;
    }
}
