# 💎 MinecraftGemini

A forked and enhanced version of the [MinecraftGemini](https://github.com/Lucas-X-A/MinecraftGemini_Minecraft_Mod) mod, bringing the power of Google's Gemini AI directly into your Minecraft chat!

This modified version expands on the original by adding powerful new features, including the ability for Gemini to "see" your game and interact more naturally with the server.

## ✨ Features

- **👀 Gemini Vision:** Use the new `@GeminiVision` prefix to send a screenshot of your current game view to Gemini along with your question. Ask about your builds, inventory, or what you're looking at!
- **💬 Global Chat Support:** Gemini can now speak in the normal chat, making it fully usable on multiplayer servers.
- **📜 Full Chat Context:** Gemini has access to the full chat history since you joined, including messages from other players and system notifications, allowing for context-aware responses.
- **🕹️ Command Execution:** Support for Gemini to run commands natively and reason about the outputs.

## 🚀 Usage

Using MinecraftGemini is simple. Just type one of the following prefixes in the chat:

*   **`@Gemini <your question>`** - Ask Gemini a text-based question.
*   **`@GeminiVision <your question>`** - Ask Gemini a question and include a screenshot of what you're currently looking at.

### ⚙️ Commands

*   `/gemini config <key>` - Set your Google AI API Key.
*   `/gemini clear` - Delete the conversation history (useful to fix hallucinations or reset context).
*   `/gemini model <name>` - Switch the AI Model (e.g., `gemini-2.5-flash`, `gemini-2.5-pro`).
*   `/gemini visible` - Make Gemini's responses visible in global chat.
*   `/gemini hidden` - Make Gemini's responses private (only visible to you).
*   `/gemini help` - Show the in-game command list.

## 🛠️ Setup

1.  Get a Google Gemini API key from [Google AI Studio](https://aistudio.google.com/).
2.  In-game, use `/gemini config <YOUR_API_KEY>` to save your key.
3.  Start chatting!

## 📜 License

This project is licensed under the **GPLv3 License**.

---

*Note: This mod is not affiliated with Mojang Studios or Google.*
