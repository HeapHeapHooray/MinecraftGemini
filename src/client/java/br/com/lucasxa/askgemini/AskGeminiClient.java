package br.com.lucasxa.askgemini;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class AskGeminiClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Load Configuration on Startup
		ConfigManager.load();
		System.out.println("[AskGemini] Config loaded.");

		// Register Commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("gemini")

					// Subcommand: /gemini config <API_KEY>
					.then(ClientCommandManager.literal("config")
							.then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
									.executes(context -> {
										String newKey = StringArgumentType.getString(context, "key");
										// Save API Key to file
										ConfigManager.setApiKey(newKey);
										// User Feedback
										context.getSource().sendFeedback(
												Text.of("§a[AskGemini] API Key saved successfully!"));
										return 1;
									})
							)
					)

					// Subcommand: /gemini clear (Reset Conversation Context)
					.then(ClientCommandManager.literal("clear")
							.executes(context -> {
								GeminiIntegration.clearHistory();
								context.getSource().sendFeedback(
										Text.of("§a[AskGemini] Conversation history cleared! Context reset."));
								return 1;
							})
					)

					// Subcommand: /gemini help (Show Command List)
					.then(ClientCommandManager.literal("help")
							.executes(context -> {
								var source = context.getSource();
								source.sendFeedback(Text.of("§b§l--- AskGemini Help ---"));
								source.sendFeedback(Text.of("§e/gemini config <key> §7- Set your Google AI API Key."));
								source.sendFeedback(Text.of("§e/gemini clear §7- Delete conversation history (fix hallucinations)."));
								source.sendFeedback(Text.of("§e/gemini help §7- Show this command list."));
								source.sendFeedback(Text.of("§bUsage: §fSimply type §e@Gemini <question> §fin chat."));
								return 1;
							})
					)
			);
		});

		// Register Chat Listener
		ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
			if (message.startsWith("@Gemini ") || message.startsWith("@gemini ") || message.startsWith("@GEMINI ")) {
				MinecraftClient client = MinecraftClient.getInstance();

				// Check if API Key exists
				if (!ConfigManager.hasKey()) {
                    if (client.player != null) {
                        client.player.sendMessage(
                                Text.of("§c[AskGemini] Error: API Key not configured. Use: /gemini config <YOUR_API_KEY>"),
                                false
                        );
                    }
                    return false;
				}

                if (client.player != null) {
                    String playerName = client.player.getName().getString();
					Text userChatEntry = Text.of("<" + playerName + "> " + message);
					client.inGameHud.getChatHud().addMessage(userChatEntry);
                }

				String question = message.substring(8);

				// Visual "Thinking..." feedback
                if (client.player != null) {
                    client.player.sendMessage(Text.of("§7§o[Gemini] Thinking..."), false);
                }

                // Call API using the saved Key
				GeminiIntegration.askGemini(question, ConfigManager.getApiKey())
						.thenAccept(response -> {
							// Asynchronous task
							client.execute(() -> {
								if (client.player == null) return;

								String prefixColor = "§b"; // Blue
								String textColor = "§f"; // White

								if (response.startsWith("Too many requests")) {
									prefixColor = "§6"; // Golden
									textColor = "§e";   // Yellow
								}
								else if (response.startsWith("Error parsing AI") ||
										 response.startsWith("Invalid API Key") ||
										 response.startsWith("Gemini unavailable") ||
									     response.startsWith("API Error") ||
									     response.startsWith("Connection Error") ||
								         response.startsWith("Error: Message blocked") ||
										 response.startsWith("Error: No response")) {
									prefixColor = "§c"; // Red
									textColor = "§c"; // Red
								}

								// Divides the response into lines for better formatting
                                String[] paragraphs = response.split("\n");

                                boolean isFirstLine = true;

                                for (String paragraph : paragraphs) {
                                    if (paragraph.trim().isEmpty()) continue;

                                    // Wrap long lines
                                    List<String> wrappedLines = wrapText(paragraph, 100);

                                    for (String visualLine : wrappedLines) {
                                        if (isFirstLine) {
                                            client.player.sendMessage(Text.of(prefixColor + "[Gemini] " + textColor + visualLine), false);
                                            isFirstLine = false;
                                        } else {
                                            client.player.sendMessage(Text.of(textColor + " " + visualLine), false);
                                        }
                                    }
                                }
							});
						});

				return false; // Prevent the message from being sent to the multiplayer server
			}
			return true; // Allow normal chat messages
		});
	}

    // Simple Word Wrap Implementation
    private List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // If adding the next word exceeds the limit, start a new line
            if (currentLine.length() + word.length() + 1 > maxChars) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            }
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
}