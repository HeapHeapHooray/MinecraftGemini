package br.com.lucasxa.askgemini;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("askgemini.json").toFile();
    private static String currentModel = "gemini-2.5-flash";
    private static String apiKey = "";

    // Load config from disk
    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save(); // Create empty file if not exists
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json != null) {
                // Load API Key
                if (json.has("apiKey")) {
                    String encodedKey = json.get("apiKey").getAsString();
                    // Try to decode (Base64 -> Normal Text)
                    try {
                        byte[] decodedBytes = Base64.getDecoder().decode(encodedKey);
                        apiKey = new String(decodedBytes, StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        // If fails, it assumes it's plain text so as not to break it.
                        System.err.println("[AskGemini] Config warning: Key might not be encoded. Fixing on next save.");
                        apiKey = encodedKey;
                    }
                }

                // Load Model
                if (json.has("model")) {
                    currentModel = json.get("model").getAsString();
                }
            }
        } catch (IOException e) {
            System.err.println("[AskGemini] Failed to load config: " + e.getMessage());
        }
    }

    // Save config to disk
    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonObject json = new JsonObject();

            // Encode key before saving (Normal Text -> Base64)
            if (apiKey != null && !apiKey.isEmpty()) {
                String encodedKey = Base64.getEncoder().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
                json.addProperty("apiKey", encodedKey);
            } else {
                json.addProperty("apiKey", "");
            }

            // Save current model
            if (currentModel != null && !currentModel.isEmpty()) {
                json.addProperty("model", currentModel);
            } else {
                json.addProperty("model", "gemini-2.5-flash");
            }

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
        return apiKey != null && !apiKey.isBlank();
    }

    public static String getModel() {
        return currentModel;
    }

    public static void setModel(String model) {
        currentModel = model;
        save(); // Auto-save when setting
    }
}