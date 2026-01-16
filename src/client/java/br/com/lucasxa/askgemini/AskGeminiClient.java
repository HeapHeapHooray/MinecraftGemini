package br.com.lucasxa.askgemini;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class AskGeminiClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Registers the event that listens to chat messages
		ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {

			// Checks if the message starts with the "@Gemini " trigger
			if (message.startsWith("@Gemini ")) {

				String question = message.substring(8); // Removes the prefix

				// Sends visual feedback to the player
				if (MinecraftClient.getInstance().player != null) {
					MinecraftClient.getInstance().player.sendMessage(
							Text.of("§7§o[AskGemini] Thinking: " + question),
							false
					);
				}

				System.out.println("Question captured by Mod: " + question);

				// Returns false to CANCEL sending the message to the server
				return false;
			}

			// Returns true to let normal messages pass through
			return true;
		});
	}
}