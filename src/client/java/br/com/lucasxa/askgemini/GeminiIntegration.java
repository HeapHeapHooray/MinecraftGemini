package br.com.lucasxa.askgemini;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class GeminiIntegration {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // Reusable HTTP Client
    private static final HttpClient client = HttpClient.newHttpClient();

    public static CompletableFuture<String> askGemini(String question, String apiKey) {
        // Construct the JSON request body
        // Structure: { "contents": [{ "parts": [{ "text": "QUESTION" }] }] }
        JsonObject jsonBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject partObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textObj = new JsonObject();

        String systemInstruction = " (Don't give a much long answer. Give the answer in the language of the initial part of the prompt.)";
        textObj.addProperty("text", question + systemInstruction);
        parts.add(textObj);
        partObj.add("parts", parts);
        contents.add(partObj);
        jsonBody.add("contents", contents);

        // Create the HTTP POST Request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .build();

        // Send Asynchronously
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();

                    // SUCCESS (200 OK)
                    if (status == 200) {
                        return extractTextFromJson(response.body());
                    }

                    // ERROR 400: Bad Request (Likely invalid API Key)
                    else if (status == 400) {
                        return "Invalid API Key. Please check your config using /gemini config";
                    }

                    // ERROR 429: Too Many Requests (Spamming or Quota exceeded)
                    else if (status == 429) {
                        return "Too many requests! Please wait a moment before asking again.";
                    }

                    // ERROR 500+: Google Server Issues
                    else if (status >= 500) {
                        return "Google Gemini is currently unavailable. Try again later.";
                    }

                    // Generic Error
                    else {
                        return "API Error (" + status + "): " + response.body();
                    }
                })
                .exceptionally(e -> "Connection Error: " + e.getMessage());
    }

    // Helper method to parse the response JSON and extract only the text
    private static String extractTextFromJson(String jsonResponse) {
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            String rawText = json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
            return convertMarkdownToMinecraft(rawText);
        } catch (Exception e) {
            return "Error parsing AI response.";
        }
    }

    /**
     * Converts basic Markdown syntax to Minecraft Legacy Formatting Codes.
     * **Bold** -> §lBold§r
     * *Italic* -> §oItalic§r
     * `Code`   -> §7Code§r (Gray)
     */
    private static String convertMarkdownToMinecraft(String text) {
        if (text == null || text.isEmpty()) return text;

        // Headers (### Title) -> Bold & Underline
        text = text.replaceAll("(?m)^#{1,6}\\s+(.*)", "§n§l$1§r");

        // Bold (**text**) -> §l (Bold)
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "§l$1§r");

        // Italic (*text*) -> §o (Italic)
        text = text.replaceAll("\\*(.*?)\\*", "§o$1§r");

        // Inline Code (`text`) -> §7 (Gray) for code look
        text = text.replaceAll("`(.*?)`", "§7$1§r");

        // Code Blocks (```) -> Remove the triple backticks (clean up)
        text = text.replace("```java", "")
                .replace("```json", "")
                .replace("```", "");

        // List Items (- item) -> Use a bullet point
        text = text.replaceAll("(?m)^\\s*-\\s+", "• ");

        return text.trim();
    }
}