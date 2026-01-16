package br.com.lucasxa.askgemini;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class AskGeminiClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Registra o evento que "escuta" o chat
		ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {

			// Verifica se a mensagem começa com o gatilho "@Gemini "
			if (message.startsWith("@Gemini ")) {

				String pergunta = message.substring(8); // Remove o prefixo

				// Envia feedback visual para o jogador
				if (MinecraftClient.getInstance().player != null) {
					MinecraftClient.getInstance().player.sendMessage(
							Text.of("§7§o[AskGemini] Pensando: " + pergunta),
							false
					);
				}

				System.out.println("Pergunta capturada pelo Mod: " + pergunta);

				// Retorna false para CANCELAR o envio da mensagem ao servidor
				return false;
			}

			// Retorna true para deixar mensagens normais passarem
			return true;
		});
	}
}