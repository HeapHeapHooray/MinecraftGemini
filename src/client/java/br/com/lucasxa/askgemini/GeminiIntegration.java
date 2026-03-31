package br.com.lucasxa.askgemini;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.mojang.brigadier.ParseResults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

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


    public static CompletableFuture<String> promptGemini(List<String> messages,String apiKey, String modelId) {

        // Dynamic URL based on the model selected
        String dynamicUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent";

        // Build JSON Body
        JsonObject jsonBody = new JsonObject();

        // Defines the system instruction of the AI
        JsonObject systemInstObj = new JsonObject();
        JsonObject partObj = new JsonObject();
        String SYSTEM_PROMPT =
                "You are an assistant inside Minecraft. The user's request is the last message starting with \"@gemini\" (case-insensitive), follow the request classifying the next thought as INTERMEDIATE or as the eventual FINAL." +
                        "Return a JSON object with fields: mode (INTERMEDIATE|FINAL), message (plain text), and commands (array). " +
                        "For INTERMEDIATE, return the next step needed to finish the request, commands sent will be run."  +
                        "if this is the last step or past the last step to conclude the request , then its FINAL, return the message that will be shown to the user (this is the result of your finished thought process), there are NO commands in FINAL mode.";
        partObj.addProperty("text",SYSTEM_PROMPT
        );
        JsonArray partsArr = new JsonArray();
        partsArr.add(partObj);
        systemInstObj.add("parts", partsArr);
        jsonBody.add("system_instruction", systemInstObj);

        // Contents (History)
        JsonArray contents = new JsonArray();
        synchronized (messages) {
            for (String message : messages) {
                contents.add(createMessageNoRole(message));
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
        generationConfig.addProperty("maxOutputTokens", 12000);

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

        generationConfig.addProperty("responseMimeType","application/json");
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
                            // Extract text for display
                            String visualText = extractTextForDisplay(contentObj);

                            AskGeminiClient.logMessage(Text.of(visualText));

                            JsonObject jsonObject = new Gson().fromJson(visualText, JsonObject.class);

                            String mode = jsonObject.get("mode").getAsString();

                            if("FINAL".equals(mode)) {
                                if(AskGeminiClient.waitingForCommand) {
                                    AskGeminiClient.waitingForCommand = false;
                                    return jsonObject.get("message").getAsString();
                                }
                                else {
                                    return "EMPTY";
                                }
                            }
                            else if("INTERMEDIATE".equals(mode) && AskGeminiClient.waitingForCommand) {

                                JsonArray commandsArray = jsonObject.getAsJsonArray("commands");

                                List<String> commands = new ArrayList<>();

                                for (JsonElement element : commandsArray) {
                                    String command = element.getAsString();

                                    if (command.startsWith("/")) {
                                        command = command.substring(1);
                                    }

                                    commands.add(command);
                                }


                                for (String command : commands) {
                                    MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command);
                                }

                                return jsonObject.get("message").getAsString();
                            }
                            return "EMPTY";
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
                                            return "This model requires a Paid API Key (Free tier not supported).";
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


    private static JsonObject createMessageNoRole(String text) {
        text = text + "\n";
        JsonObject message = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        parts.add(part);
        message.add("parts", parts);
        return message;
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