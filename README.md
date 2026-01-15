# AskGemini

![Java](https://img.shields.io/badge/Java-21-orange)
![Fabric](https://img.shields.io/badge/Loader-Fabric-box)
![License](https://img.shields.io/badge/License-GPLv3-blue)

**AskGemini** is a Minecraft Fabric mod that integrates Google's Gemini AI directly into the in-game chat. It allows players to ask questions, get building tips, or just chat with the AI without ever leaving the game world.

## 🚀 Features

* **In-Game AI Chat:** Intercepts chat messages starting with specific prefixes to communicate with Gemini.
* **Seamless Integration:** Use `@Gemini <your question>` to talk to the AI.
* **Asynchronous Processing:** Queries are processed in a separate thread to ensure the game **never freezes** or lags while waiting for a response.
* **Secure Configuration:** Supports custom API Keys via in-game commands.

## 🛠️ Installation (For Players)

1.  **Prerequisites:**
    * Make sure you have [Minecraft Java Edition](https://www.minecraft.net/) installed.
    * Install the [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.11**.
    * Download the [Fabric API](https://modrinth.com/mod/fabric-api) mod (required).

2.  **Download AskGemini:**
    * Download the latest `.jar` file from the [Releases](link-to-your-releases-page) tab.

3.  **Install:**
    * Place the `.jar` file into your Minecraft `mods` folder.

## 🔑 Configuration (API Key)

To use the mod, you need a free Google Gemini API Key.

1.  Go to [Google AI Studio](https://aistudio.google.com/app/apikey) and create a new API Key.
2.  Launch Minecraft with the mod installed.
3.  In the game, run the following command (only needed once):
    ```
    /gemini config <YOUR_API_KEY_HERE>
    ```
    *Example: `/gemini config AIzaSyD...`*
4.  The key will be saved locally. You are now ready to chat!

## 🎮 Usage

Simply open the chat and type:

```text
@Gemini How do I build a redstone clock?
```
Or
```text
@Gemini Tell me a story about a creeper who is afraid of exploding.
```
The AI response will appear in your local chat (visible only to you).

