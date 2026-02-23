# AskGemini

![Java](https://img.shields.io/badge/Java-21-orange)
![Fabric](https://img.shields.io/badge/Loader-Fabric-box)
![License](https://img.shields.io/badge/License-GPLv3-blue)
![Version](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-success?logo=modrinth)](https://modrinth.com/mod/askgemini)
[![CurseForge](https://img.shields.io/badge/CurseForge-Download-orange?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/askgemini)

**AskGemini** is a client-side Minecraft Fabric mod that integrates Google's Gemini AI directly into your in-game chat.

It allows players to ask questions, get crafting recipes, building tips, or just chat with the AI without ever leaving the game world. The mod is designed to be lightweight, fast, and unobtrusive.

---

## 🚀 Features

* **🧠 Contextual Conversation:** The AI remembers your chat history. You can ask follow-up questions (e.g., *"How do I craft that?"*) without repeating yourself.
* **💬 In-Game AI Chat:** Seamlessly talk to Google Gemini 2.5 Flash and other Gemini models directly from the chat HUD.
* **⚡ Zero Lag:** All requests are processed asynchronously. The game **never freezes** while waiting for a response.
* **🎨 Rich Text Support:** Automatically converts Markdown (Bold, Italic, Code Blocks) from the AI into Minecraft's legacy color formatting.
* **🔒 Secure & Private:**  API Keys are stored locally using Base64 encoding.
    * Chat messages starting with `@Gemini` are **client-side only** (they are never sent to the multiplayer server, preserving privacy).
* **🌈 Visual Feedback:** Dynamic color coding for success messages and errors messages.

## 🛠️ Installation (For Players)

1.  **Prerequisites:**
    * Make sure you have [Minecraft Java Edition](https://www.minecraft.net/) installed.
    * Install the [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.11**.
    * Download the [Fabric API](https://modrinth.com/mod/fabric-api) mod (required).

2.  **Download AskGemini:**
    * Download the latest version from the official pages of the mod:
      * [Modrinth](https://modrinth.com/mod/askgemini).
      * [CurseForge](https://www.curseforge.com/minecraft/mc-mods/askgemini)
    * *(Alternatively, you can get the `.jar` file from the [GitHub Releases](../../releases) tab).*

3.  **Install:**
    * Place the `.jar` file into your Minecraft `mods` folder.
    * Launch the game!

## 🔑 Configuration (API Key)

To use the mod, you need a free Google Gemini API Key.

1.  Go to [Google AI Studio](https://aistudio.google.com/app/apikey) and create a new API Key.
2.  Launch Minecraft with the mod installed.
3.  In the game, run the following command (only needed once):
    ```
    /gemini config <YOUR_API_KEY_HERE>
    ```
    *Example: `/gemini config AIzaSyD...`*
4.  The key will be saved locally in `config/askgemini.json`.

## ⌨️ Commands

| Command | Description |
| :--- | :--- |
| `/gemini config <API_KEY>` | Sets your Google Gemini API Key. (Required once). |
| `/gemini clear` | **Clears the conversation history.** Use this if you want to start a new topic or if the AI gets confused. |
| `/gemini help` | Displays the list of available commands and usage instructions in-game. |

## 🎮 Usage

Simply open the chat and type `@Gemini` or `@gemini` followed by your prompt:

**General Questions:**
```text
@Gemini How do I build a redstone clock?
```
**Creative Writing:**
```text
@gemini Tell me a story about a creeper who is afraid of exploding.
```
**Coding Helper:**
```text
@gemini How does the /fill command work?
```
The AI response will appear in your local chat (visible only to you).

## 💻 Building from Source (For Developers)

If you want to contribute or modify the mod, follow these steps:

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/YourUsername/AskGemini.git](https://github.com/YourUsername/AskGemini.git)
    cd AskGemini
    ```

2.  **Build the project:**
    * **Linux/macOS:**
        ```bash
        ./gradlew build
        ```
    * **Windows:**
        ```powershell
        .\gradlew build
        ```

3.  **Locate the artifact:**
    The compiled `.jar` file will be located in `build/libs/`.

## 📜 License

This project is licensed under the **GPLv3 License**.

---

*Note: This mod is not affiliated with Mojang Studios or Google.*

