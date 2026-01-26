package br.com.lucasxa.askgemini;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GeminiIntegration {

    // Gemini API Endpoint
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private static final HttpClient client = HttpClient.newHttpClient();

    // Thread-safe history list
    private static final List<JsonObject> conversationHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY_SIZE = 20;

    public static CompletableFuture<String> askGemini(String question, String apiKey) {

        conversationHistory.add(createMessage("user", question));

        // Enforce sliding window
        while (conversationHistory.size() > MAX_HISTORY_SIZE) {
            conversationHistory.remove(0); // Remove oldest user message
            conversationHistory.remove(0); // Remove oldest model response
        }

        // Build JSON Body
        JsonObject jsonBody = new JsonObject();

        // Defines the system instruction of the AI
        JsonObject systemInstObj = new JsonObject();
        JsonObject partObj = new JsonObject();
        partObj.addProperty("text",
                "You are a helpful Minecraft assistant inside the game chat. " +
                        "Keep your answers short and concise. " +
                        "Do NOT use emojis, icons, or images. " +
                        "Do NOT use complex Unicode symbols (like mathematical symbols). " +
                        "The game chat only supports standard text. " +
                        "Avoid using complex markdown. " +
                        "Always answer in the same language as the user's prompt."
        );
        JsonArray partsArr = new JsonArray();
        partsArr.add(partObj);
        systemInstObj.add("parts", partsArr);

        jsonBody.add("system_instruction", systemInstObj);

        JsonArray contents = new JsonArray();

        // Synchronized block required for iteration
        synchronized (conversationHistory) {
            for (JsonObject msg : conversationHistory) {
                contents.add(msg);
            }
        }
        jsonBody.add("contents", contents);

        // Token limit to prevent huge responses
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", 700);
        jsonBody.add("generationConfig", generationConfig);

        // Send Request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();

                    // Successful Response
                    if (status == 200) {
                        String rawAiResponse = extractRawText(response.body());

                        if (rawAiResponse != null) {
                            conversationHistory.add(createMessage("model", rawAiResponse));
                            return convertMarkdownToMinecraft(rawAiResponse);
                        }
                        return "Error parsing AI response.";
                    } else {
                        // Rollback history on error
                        rollbackHistory();

                        // Handle Errors
                        if (status == 400) return "Invalid API Key or Bad Request.";
                        if (status == 429) return "Too many requests (Quota exceeded). Wait a bit.";
                        if (status >= 500) return "Gemini unavailable. Try again later.";
                        return "API Error (" + status + ")";
                    }
                })
                .exceptionally(e -> {
                    rollbackHistory();
                    return "Connection Error: " + e.getMessage();
                });
    }

    // Helper method to delete last user message
    private static void rollbackHistory() {
        if (!conversationHistory.isEmpty()) {
            conversationHistory.remove(conversationHistory.size() - 1);
        }
    }

    // Clears the entire conversation history
    public static void clearHistory() {
        conversationHistory.clear();
    }

    // Creates a message object for the Gemini API
    private static JsonObject createMessage(String role, String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        parts.add(part);
        message.add("parts", parts);
        return message;
    }

    // Extracts the raw text from the JSON response
    private static String extractRawText(String jsonResponse) {
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            // Verify if response is blocked by safety filters
            if (!json.has("candidates") || json.getAsJsonArray("candidates").isEmpty()) {
                return "Blocked by Safety Filters.";
            }
            return json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    // Converts Markdown formatting to Minecraft formatting text
    private static String convertMarkdownToMinecraft(String text) {
        if (text == null || text.isEmpty()) return text;

        // Code blocks
        text = text.replaceAll("```[a-zA-Z]*", "");

        // Headers
        text = text.replaceAll("(?m)^#{1,6}\\s+(.*)", "§n§l$1§r");

        // Bold
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "§l$1§r");

        // Italic
        text = text.replaceAll("\\*(.*?)\\*", "§o$1§r");
        text = text.replaceAll("_(.*?)_", "§o$1§r");

        // Inline code
        text = text.replaceAll("`(.*?)`", "§7$1§r");

        // Bullets
        text = text.replaceAll("(?m)^\\s*[\\-\\*]\\s+", "• ");

        return text.trim();
    }
}