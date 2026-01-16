package br.com.lucasxa.askgemini;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("askgemini.json").toFile();

    private static String apiKey = "";

    // Load config from disk
    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save(); // Create empty file if not exists
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("apiKey")) {
                apiKey = json.get("apiKey").getAsString();
            }
        } catch (IOException e) {
            System.err.println("[AskGemini] Failed to load config: " + e.getMessage());
        }
    }

    // Save config to disk
    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonObject json = new JsonObject();
            json.addProperty("apiKey", apiKey);
            GSON.toJson(json, writer);
        } catch (IOException e) {
            System.err.println("[AskGemini] Failed to save config: " + e.getMessage());
        }
    }

    // Setters and Getters
    public static void setApiKey(String key) {
        apiKey = key;
        save(); // Auto-save when setting
    }

    public static String getApiKey() {
        return apiKey;
    }

    public static boolean hasKey() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.isBlank();
    }
}