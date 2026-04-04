# TurboClick

**Visual automation builder for macOS** — create and run screen automation tasks without writing code.

---

## What It Does

TurboClick lets you build automation workflows visually by connecting nodes on a canvas. Each node performs an action — watch for an image, click somewhere, type text, wait, show a message — and you connect them together to create a complete task.

It also has an **AI Learn** feature: record yourself doing something on screen, and Claude AI analyzes your actions and generates the node tree automatically.

---

## Features

### Build Smart Click
- Visual node canvas — drag, connect, and configure nodes
- **Watch Zone** — wait for an image to appear on screen, then click it
- **Watch Case** — monitor multiple zones, match against multiple images
- **Simple Click** — click sequences with per-point delays, drag support
- **Keyboard** — type text, press hotkeys, or send single keys
- **Wait** — fixed delay, countdown, or wait until a specific time
- **Message** — show a toast or floating alert during task execution
- **Image** — reference image node used with Watch Case
- Loop badges on every node — repeat a node N times before moving on
- Save and load tasks as `.json` files
- Save/load individual node selections

### Build Simple Click
- Auto-clicker with per-point click types (Left / Right / Middle / Drag)
- Sub-delay and after-delay per point
- Drag pairs with visual handles and arrow overlay

### AI Learn
- Record your screen actions (clicks, drags, keyboard)
- AI analyzes screenshots + actions and generates a complete node tree
- Chat with AI to adjust the generated task
- Requires an Anthropic API key

### Other
- Task Architect overlay — view and reposition all zones and click pins on your live screen
- Rubber band selection — drag to select multiple nodes, duplicate or delete as a group
- Event Log — timestamped execution log for every node
- Global stop hotkey (F8)
- Export as `.dmg` installer

---

## Requirements

- macOS (tested on macOS Sequoia)
- Java 25 (via Homebrew: `brew install openjdk`)
- `lib/jnativehook-2.2.2.jar` (included)

---

## Build & Run


# Clone the repo
git clone https://github.com/AnhVo11/TurboClick.git
cd TurboClick

# Compile and run
chmod +x build.sh
./build.sh

# Build a .dmg installer
./build.sh package


The DMG will appear in `dist/TurboClick-2.0.dmg`.

---

## AI Learn Setup

1. Get an API key at [console.anthropic.com](https://console.anthropic.com)
2. Open TurboClick → click **⚙ Settings** in the sidebar
3. Paste your API key and click Save
4. Click **⏺ AI Learn** in the toolbar to start recording
5. Do your task on screen, then click **⏹ Stop**
6. The AI analyzes your recording and builds the node tree
7. Use **✦ AI Chat** to refine the result

---

## Project Structure


src/
  engine/     Execution, rule engine, auto-clicker, AI, recording
  nodes/      Node types and logic
  ui/         Canvas, editor, palette, overlays, save/load
TurboClick.java   Main entry point
build.sh          Compile + run + package script
lib/              jNativeHook dependency


---

## License
