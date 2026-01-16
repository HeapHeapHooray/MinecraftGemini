package br.com.lucasxa.askgemini;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class AskGeminiClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Load Configuration on Startup
		ConfigManager.load();
		System.out.println("[AskGemini] Config loaded.");

		// Register Command: /gemini config <API_KEY>
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("gemini")
					.then(ClientCommandManager.literal("config")
							.then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
									.executes(context -> {
										String newKey = StringArgumentType.getString(context, "key");
										// Save safely to file
										ConfigManager.setApiKey(newKey);
										// Feedback to user
										context.getSource().sendFeedback(
												Text.of("§a[AskGemini] API Key saved successfully!"));
										return 1;
									})
							)
					)
			);
		});

		// Register Chat Listener
		ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
			if (message.startsWith("@Gemini ")) {
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

				// Visual Feedback
                if (client.player != null) {
                    client.player.sendMessage(Text.of("§7§o[Gemini] Thinking..."), false);
                }

                // Call API using the Saved Key
				GeminiIntegration.askGemini(question, ConfigManager.getApiKey())
						.thenAccept(response -> {
							// Asynchronous task
							client.execute(() -> {
								String prefixColor = "§b"; // Blue
								String textColor = "§f"; // White

								if (response.startsWith("Invalid API Key") ||
									response.startsWith("Too many requests") ||
									response.startsWith("Google Gemini is currently unavailable") ||
									response.startsWith("API Error") ||
									response.startsWith("Connection Error")) {

									prefixColor = "§c"; // Red
									textColor = "§c";
								}
								client.player.sendMessage(
										Text.of(prefixColor + "[Gemini] " + textColor + response),
										false
								);
							});
						});

				return false;
			}
			return true;
		});
	}
}