package br.com.HeapHeapHooray.askgemini;

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

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.Base64;
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
	public static boolean isThinking = false;
	public static String currentBase64Image = null;
	public static int sequenceCount = 0;

	public static ReentrantLock mutex = new ReentrantLock();
	private static ScheduledFuture<?> debouncedFeedTask = null;

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

	public static void captureScreenshotAsync(Consumer<String> callback) {
		MinecraftClient client = MinecraftClient.getInstance();
		ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), (image) -> {
			try {
				Path tempPath = Files.createTempFile("gemini_screenshot", ".png");
				image.writeTo(tempPath);
				byte[] bytes = Files.readAllBytes(tempPath);
				Files.delete(tempPath);
				callback.accept(Base64.getEncoder().encodeToString(bytes));
			} catch (IOException e) {
				log.error("Failed to capture screenshot", e);
				callback.accept(null);
			} finally {
				image.close();
			}
		});
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
								source.sendFeedback(Text.of("§bVision: §fType §b@GeminiVision §e<your question> §fto include a screenshot."));
								source.sendFeedback(Text.of("§b§m                                "));
								return 1;
							})
					)
.then(ClientCommandManager.literal("visible").executes(command -> { selfMessage(Text.of("Gemini messages are now visible !"),false); visible = true; return 1;}))
					.then(ClientCommandManager.literal("hidden").executes(command -> { selfMessage(Text.of("Gemini messages are now hidden !"),false); visible = false; return 1;}))
			);
		});


		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) ->
		{
			logMessage(message);
			triggerFeedGemini();
		});

		ClientReceiveMessageEvents.GAME.register((message,overlay) ->
		{
			logMessage(message);
			triggerFeedGemini();
		});
		// Register Chat Listener
		ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
			boolean isVision = message.startsWith("@GeminiVision ") || message.startsWith("@geminivision ") || message.startsWith("@GEMINIVISION ");
			if (isVision || message.startsWith("@Gemini ") || message.startsWith("@gemini ") || message.startsWith("@GEMINI ")) {
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
				int prefixLength = isVision ? 13 : 7;
				String rawPrefix = message.substring(0, prefixLength);
				String content = message.substring(prefixLength);
				Text userChatEntry = Text.of("§b" + rawPrefix + "§f" + content);
				selfMessage(userChatEntry,true);

				// Visual "Thinking..." feedback
				selfMessage(Text.of("§7§o[Gemini] Thinking..."), false);
			}

			String question = message.substring(isVision ? 14 : 8);

			try {
				mutex.lock();
				waitingForCommand = true;
				isThinking = false;
				sequenceCount = -1;
				if (isVision) {
					captureScreenshotAsync((base64) -> {
						try {
							mutex.lock();
							currentBase64Image = base64;
							feedGemini();
						} finally {
							mutex.unlock();
						}
					});
				} else {
					currentBase64Image = null;
					feedGemini();
				}
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


	public static void triggerFeedGemini() {
		try {
			mutex.lock();
			if (!waitingForCommand || isThinking) {
				return;
			}
			if (debouncedFeedTask != null && !debouncedFeedTask.isDone()) {
				debouncedFeedTask.cancel(false);
			}
			debouncedFeedTask = scheduler.schedule(() -> {
				try {
					mutex.lock();
					if (waitingForCommand && !isThinking) {
						feedGemini();
					}
				} finally {
					mutex.unlock();
				}
			}, 500, TimeUnit.MILLISECONDS);
		} finally {
			mutex.unlock();
		}
	}

	public static void feedGemini() {
		try {
			mutex.lock();
			if (isThinking) return;
			isThinking = true;
			sequenceCount += 1;
			if(sequenceCount>=10)
			{
				waitingForCommand = false;
				isThinking = false;
				currentBase64Image = null;
				return;
			}
		} finally {
			mutex.unlock();
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
		GeminiIntegration.promptGemini(messages, ConfigManager.getApiKey(), ConfigManager.getModel(), currentBase64Image)
				.thenAccept(response -> {
					// Cancel the "Still thinking..." message
					slowResponseTask.cancel(false);

					// Asynchronous task
					client.execute(() -> {
						try {
							mutex.lock();
							isThinking = false;
						} finally {
							mutex.unlock();
						}

						if (client.player == null) return;

						String prefixColor = "§b"; // Blue
						String textColor = "§f"; // White

						boolean isError = false;
						if (response.startsWith("Too many requests")) {
							prefixColor = "§6"; // Golden
							textColor = "§e";   // Yellow
							isError = true;
						} else if (response.startsWith("Error parsing AI") ||
								response.startsWith("Invalid API Key") ||
								response.startsWith("Gemini unavailable") ||
								response.startsWith("API Error") ||
								response.startsWith("Connection Error") ||
								response.startsWith("Error:") ||
								response.startsWith("This model requires a Paid API Key")) {
							prefixColor = "§c"; // Red
							textColor = "§c"; // Red
							isError = true;
						}

						if (isError) {
							try {
								mutex.lock();
								waitingForCommand = false;
							} finally {
								mutex.unlock();
							}
						}

						if(!"EMPTY".equals(response))
						{
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
						}

						// After processing response, if still waiting for command, trigger another check
						// This handles cases where commands might not have produced output or we want to continue.
						triggerFeedGemini();
					});
				});
	}




    // Simple Word Wrap Implementation
    private static List<String> wrapText(String text, int maxChars) {
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