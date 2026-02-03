package br.com.lucasxa.askgemini;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

    private static final HttpClient client = HttpClient.newHttpClient();
    // Thread-safe history list
    private static final List<JsonObject> conversationHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY_SIZE = 20;

    public static CompletableFuture<String> askGemini(String question, String apiKey, String modelId) {

        // Dynamic URL based on the model selected
        String dynamicUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent";

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

        // Contents (History)
        JsonArray contents = new JsonArray();
        synchronized (conversationHistory) {
            for (JsonObject msg : conversationHistory) {
                contents.add(msg);
            }
        }
        jsonBody.add("contents", contents);

        JsonArray safetySettings = new JsonArray();
        safetySettings.add(createSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"));
        safetySettings.add(createSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"));
        safetySettings.add(createSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"));
        safetySettings.add(createSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE"));
        jsonBody.add("safetySettings", safetySettings);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", 1200);

        // Model-specific thinking configurations
        if (modelId.startsWith("gemini-3")) {
            JsonObject thinkingConfig = new JsonObject();

            if (modelId.contains("flash")) {
                thinkingConfig.addProperty("thinkingLevel", "medium");
            }
            else if (modelId.contains("pro")) {
                thinkingConfig.addProperty("thinkingLevel", "high");
            }

            generationConfig.add("thinkingConfig", thinkingConfig);
        }

        jsonBody.add("generationConfig", generationConfig);

        // Send Request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dynamicUrl + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();

                    // Successful Response
                    if (status == 200) {
                        try {
                            // Parse JSON Response
                            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();

                            if (!responseJson.has("candidates") || responseJson.getAsJsonArray("candidates").isEmpty()) {
                                return "Error: No response from AI.";
                            }

                            JsonObject candidate = responseJson.getAsJsonArray("candidates").get(0).getAsJsonObject();

                            if (!candidate.has("content")) {
                                String finishReason = candidate.has("finishReason") ? candidate.get("finishReason").getAsString() : "UNKNOWN";
                                return "Error: Message blocked (" + finishReason + ")";
                            }

                            // Extract content object
                            JsonObject contentObj = candidate.getAsJsonObject("content");

                            // Save response to history
                            conversationHistory.add(contentObj);

                            // Extract text for display
                            String visualText = extractTextForDisplay(contentObj);

                            return convertMarkdownToMinecraft(visualText);
                        } catch (Exception e) {
                            return "Error parsing AI response: " + e.getMessage();
                        }
                    } else {
                        // Rollback history on error
                        rollbackHistory();

                        // Handle Errors
                        if (status == 400) return "Invalid API Key or Bad Request.";
                        if (status == 429) {
                            try {
                                // Try to parse error message for more details
                                JsonObject errorJson = JsonParser.parseString(response.body()).getAsJsonObject();

                                if (errorJson.has("error")) {
                                    JsonObject errorObj = errorJson.getAsJsonObject("error");

                                    if (errorObj.has("message")) {
                                        String msg = errorObj.get("message").getAsString();

                                        if (modelId.contains("pro") && msg.contains("limit: 0")) {
                                            // Specific message for Pro model with zero quota
                                            return "Error: This model requires a Paid API Key (Free tier not supported).";
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore parsing errors and use generic message
                            }
                            return "Too many requests (Quota exceeded). Wait a bit.";
                        }
                        if (status >= 500) return "Gemini unavailable. Try again later.";
                        return "API Error (" + status + ")";
                    }
                })
                .exceptionally(e -> {
                    rollbackHistory();
                    return "Connection Error: " + e.getMessage();
                });
    }

    // Helper method to create safety setting object
    private static JsonObject createSafetySetting(String category, String threshold) {
        JsonObject setting = new JsonObject();
        setting.addProperty("category", category);
        setting.addProperty("threshold", threshold);
        return setting;
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

    // Extracts only the text content for display from the response content object
    private static String extractTextForDisplay(JsonObject content) {
        StringBuilder sb = new StringBuilder();
        if (content.has("parts")) {
            JsonArray parts = content.getAsJsonArray("parts");
            for (JsonElement p : parts) {
                JsonObject part = p.getAsJsonObject();
                // Ignores other content types
                if (part.has("text")) {
                    sb.append(part.get("text").getAsString());
                }
            }
        }
        return sb.toString();
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

        // Bullet points
        text = text.replaceAll("(?m)^\\s*[\\-*]\\s+", "• ");

        return text.trim();
    }
}