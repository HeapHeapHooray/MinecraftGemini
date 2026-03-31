package br.com.lucasxa.askgemini;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.network.message.MessageType;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.Collections;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.world.waypoint.ServerWaypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class AskGeminiClient implements ClientModInitializer {

	// Scheduler for delayed tasks
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


	public static List<String> messages = Collections.synchronizedList(new ArrayList<>());

	public static  boolean visible = false;
	private static final Logger log = LoggerFactory.getLogger(AskGeminiClient.class);

	public static boolean waitingForCommand = false;
	public static int sequenceCount = 0;

	public static ReentrantLock mutex = new ReentrantLock();

	public static void logMessage(Text message) {
		String plainText = message.getString();
			messages.add(plainText);
	}

	public static void selfMessage(Text message, Boolean log) {
		MinecraftClient client = MinecraftClient.getInstance();

		client.player.sendMessage(message,false);

		if(log)
		{
		logMessage(message);
		}


	}
	@Override
	public void onInitializeClient() {
		// Load Configuration on Startup

		ConfigManager.load();
		System.out.println("[AskGemini] Config loaded.");



		// Register Commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

			// Model Suggestions Provider
			SuggestionProvider<FabricClientCommandSource> MODEL_SUGGESTIONS = (context, builder) -> {
				builder.suggest("gemini-2.5-flash", Text.of("§bBest cost-benefit model: §7Comprehensive features, low-latency processing."));
				builder.suggest("gemini-2.5-flash-lite", Text.of("§bFastest flash model: §7Optimized for cost efficiency and high processing capacity."));
				builder.suggest("gemini-2.5-pro", Text.of("§bReasoning model: §7Smarter, advanced reasoning. §c§l[REQUIRES PAID API KEY]"));
				builder.suggest("gemini-3-flash-preview", Text.of("§bMost balanced model: §7Faster and cutting-edge intelligence."));
				builder.suggest("gemini-3-pro-preview", Text.of("§bBest model for multimodal understanding: §7Deeper interactivity. §c§l[REQUIRES PAID API KEY]"));
				return builder.buildFuture();
			};

			dispatcher.register(ClientCommandManager.literal("gemini")

					// Subcommand: /gemini config <API_KEY>
					.then(ClientCommandManager.literal("config")
							.then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
									.executes(context -> {
										String newKey = StringArgumentType.getString(context, "key");
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
								messages = Collections.synchronizedList(new ArrayList<>());
								context.getSource().sendFeedback(
										Text.of("§a[AskGemini] Conversation history cleared! Context reset."));
								return 1;
							})
					)

					// Subcommand: /gemini model <modelId> (Switch AI Model)
					.then(ClientCommandManager.literal("model")
							.then(ClientCommandManager.argument("modelId", StringArgumentType.string())
									.suggests(MODEL_SUGGESTIONS)
									.executes(context -> {
										String newModel = StringArgumentType.getString(context, "modelId");
										ConfigManager.setModel(newModel); // Save model to config
										context.getSource().sendFeedback(
												Text.of("§a[AskGemini] Model switched to: §b" + newModel));
										return 1;
									})
							)
					)

					// Subcommand: /gemini help (Show Command List)
					.then(ClientCommandManager.literal("help")
							.executes(context -> {
								var source = context.getSource();
								source.sendFeedback(Text.of("§b§m      §r §bAskGemini Help §r§b§m      "));
								source.sendFeedback(Text.of("§e/gemini config <key> §7- Set your Google AI API Key."));
								source.sendFeedback(Text.of("§e/gemini clear §7- Delete conversation history (fix hallucinations)."));
								source.sendFeedback(Text.of("§e/gemini model <name> §7- Switch AI Model."));
								source.sendFeedback(Text.of("§e/gemini help §7- Show this command list."));
								source.sendFeedback(Text.of("§bUsage: §fSimply type §b@gemini §e<your question> §fin chat."));
								source.sendFeedback(Text.of("§b§m                                "));
								return 1;
							})
					).then(ClientCommandManager.literal("visible").executes(command -> { selfMessage(Text.of("Gemini messages are now visible !"),false); visible = true; return 1;}))
					.then(ClientCommandManager.literal("hidden").executes(command -> { selfMessage(Text.of("Gemini messages are now hidden !"),false); visible = false; return 1;}))
			);
		});


		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) ->
		{
			logMessage(message);
			try {
				mutex.lock();
				if (waitingForCommand) {
					feedGemini();
				}
			}
			finally {
				mutex.unlock();
			}
		});

		ClientReceiveMessageEvents.GAME.register((message,overlay) ->
		{
			logMessage(message);
			try {
			mutex.lock();
			if (waitingForCommand) {
				feedGemini();
			}
		}
		finally {
			mutex.unlock();
		}
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
				// Echo user's question in chat
				String playerName = client.player.getName().getString();
				String rawPrefix = message.substring(0, 7);
				String content = message.substring(7);
				Text userChatEntry = Text.of("§b" + rawPrefix + "§f" + content);
				selfMessage(userChatEntry,true);

				// Visual "Thinking..." feedback
				selfMessage(Text.of("§7§o[Gemini] Thinking..."), false);
			}

			String question = message.substring(8);

			try {
				mutex.lock();
				waitingForCommand = true;
				sequenceCount = -1;
				feedGemini();
			}
			finally {
				mutex.unlock();
			}

			return visible;
		}
			logMessage(Text.of(message));
			return true; // Allow normal chat messages
		}
		);
	}


	public void feedGemini() {
		sequenceCount += 1;
		if(sequenceCount>=10)
		{
			waitingForCommand = false;
		}
			MinecraftClient client = MinecraftClient.getInstance();
			// Schedule delayed "Still thinking..." message
			ScheduledFuture<?> slowResponseTask = scheduler.schedule(() -> {
				// Asynchronous task
				client.execute(() -> {
					if (client.player != null) {
							/*client.player.sendMessage(
									Text.of("§7§o[Gemini] Still thinking..."),
									false
							);*/
					}
				});
			}, 15, TimeUnit.SECONDS);

			// Call API using the saved Key
			GeminiIntegration.promptGemini(messages, ConfigManager.getApiKey(), ConfigManager.getModel())
					.thenAccept(response -> {
						// Cancel the "Still thinking..." message
						slowResponseTask.cancel(false);

						// Asynchronous task
						client.execute(() -> {
							if (client.player == null) return;

							String prefixColor = "§b"; // Blue
							String textColor = "§f"; // White

							if (response.startsWith("Too many requests")) {
								prefixColor = "§6"; // Golden
								textColor = "§e";   // Yellow
							} else if (response.startsWith("Error parsing AI") ||
									response.startsWith("Invalid API Key") ||
									response.startsWith("Gemini unavailable") ||
									response.startsWith("API Error") ||
									response.startsWith("Connection Error") ||
									response.startsWith("Error:") ||
									response.startsWith("This model requires a Paid API Key")) {
								prefixColor = "§c"; // Red
								textColor = "§c"; // Red
							}

							if("EMPTY".equals(response))
							{
								return;
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
										Text t = Text.of("[Gemini] " + visualLine);
										logMessage(t);
										if(!visible) { selfMessage(t,false); }
										else {
											MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(t.getString());

										}
										isFirstLine = false;
									} else {
										Text t = Text.of(" " + visualLine);
										logMessage(t);
										if(!visible) { selfMessage(t,false); }
										else {
											MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(t.getString());
										}
									}
								}
							}
						});
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