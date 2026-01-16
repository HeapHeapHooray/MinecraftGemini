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

        textObj.addProperty("text", question);
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
                    if (response.statusCode() == 200) {
                        return extractTextFromJson(response.body());
                    } else {
                        return "API Error: " + response.statusCode();
                    }
                })
                .exceptionally(e -> "Connection Error: " + e.getMessage());
    }

    // Helper method to parse the response JSON and extract only the text
    private static String extractTextFromJson(String jsonResponse) {
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            return json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            return "Error reading AI response.";
        }
    }
}