# TurboClick — Full Project Summary

## What It Is
A Java macOS desktop automation app with two modes:
1. **Build Smart Click** — visual node canvas to build automation flows
2. **Build Simple Click** — auto-clicker with per-point click types

## Build Command
```bash
chmod +x build.sh && ./build.sh          # compile + run
./build.sh package                        # compile + build DMG → dist/TurboClick-2.0.dmg
# Requires: lib/jnativehook-2.2.2.jar
# Requires: TurboClick.icns (generated via iconutil from icon.iconset/)
# dist/ is excluded from git via .gitignore
```

## File Structure
```
src/
  engine/   ExecutionContext.java, RuleEngine.java, SimpleClickEngine.java,
            RecordingEngine.java, AIAnalyzer.java
  nodes/    BaseNode.java, NodeFactory.java, ImageNode.java, WatchCaseNode.java
  ui/       NodeCanvas.java, NodeEditor.java, NodePalette.java,
            SimpleClickPanel.java, TaskArchitectOverlay.java, TreeTab.java,
            SettingsPanel.java, SaveFormat.java, TaskSerializer.java
TurboClick.java
build.sh
lib/jnativehook-2.2.2.jar
.vscode/settings.json
```

---

## What's Been Built & Changed

### BaseNode.java (src/nodes/)
- NodeType enum: `WATCH_ZONE, CLICK, SIMPLE_CLICK, CONDITION, LOOP, WAIT, MESSAGE, KEYBOARD, IMAGE, WATCH_CASE`
- STOP node removed — replaced by MESSAGE node
- `public int nodeLoopCount = 1`
- `runState` is public volatile
- `isRunStateExpired(long ms)`
- `inputs` list (public)
- Geometry: `bounds()`, `inputAnchor()`, `outputAnchor()`, `leftInputAnchor()`, `rightOutputAnchor()`
- `setPortTarget()`, `addInputPort()`
- NodePort two-arg constructor

### RuleEngine.java (src/engine/)
- Per-node loop up to `nodeLoopCount` times
- Early exit on any success; follows "Not Found" only if all iterations fail

### SimpleClickEngine.java (src/engine/)
- Points: `{x, y, clicks, subDelayMs, afterDelayMs, btnType}`
- btnType: 0=Left, 1=Right, 2=Middle, 3=Drag
- Smooth 20-step drag move

### RecordingEngine.java (src/engine/) — NEW
- Captures mouse clicks + full-screen screenshots via jNativeHook
- `RecordedAction`: type (LEFT/RIGHT/DOUBLE/DRAG), coords, timestamp, screenshotBase64
- Drag detection via 10px threshold, double-click via 300ms window
- `start()` / `stop()` / `getActions()` / `isRecording()` / `setOnActionRecorded()`

### AIAnalyzer.java (src/engine/) — NEW
- Sends recordings to Claude Vision API (claude-sonnet-4-20250514)
- `analyzeRecording(actions)` — multimodal prompt with action list + screenshots
- `callAPI(system, contentJson)` — hand-rolled HTTP to Anthropic API
- `buildChatSystemPrompt(currentTaskJson)` — context-aware chat prompt
- Max 8 screenshots per analysis (evenly sampled)
- System prompt: combine all clicks into ONE SIMPLE_CLICK, KEYBOARD for typing, WATCH_ZONE for waiting, WAIT for pauses, MESSAGE at end
- Schema hint covers all node types with correct field structure

### ExecutionContext.java (src/engine/)
- `LogCallback` interface — fires on every `setHudStatus()` call
- `setLogCallback()` — wires Event Log panel in TreeTab

### SettingsPanel.java (src/ui/) — NEW
- Stores Anthropic API key in `~/.turboclick/config.properties`
- `getApiKey()` / `setApiKey()` / `hasApiKey()` / `save()`
- Settings dialog with password field + show/hide toggle

### SaveFormat.java (src/ui/) — NEW
- Hand-rolled data-class container, no external JSON library
- `TaskFile`, `NodeData`, `WatchCaseData`, `WatchZoneData`, `ArrowData`
- MESSAGE node fields fully represented
- VERSION = 1

### TaskSerializer.java (src/ui/) — NEW
- `saveTask()`, `saveNodes()`, `load(File)`, `load(String)`, `applyToCanvas()`
- Hand-rolled JSON writer + recursive parser
- Full reflection-based serialization for all node types
- MESSAGE node fully serialized/deserialized

### TaskArchitectOverlay.java (src/ui/) — NEW
- Full-screen transparent overlay showing Watch Zone rects + click pins
- RectItem: dashed border, 8 resize handles; PinItem: crosshair + color ring
- Drag to move, handles to resize, right-click to delete
- HUD bar with Done button; ESC closes
- MESSAGE nodes excluded from overlay
- Writes back via Runnable callbacks (no runtime reflection)

### NodeCanvas.java (src/ui/)
- Loop badge (↺ N) top-right; click to open spinner popup
- **Rubber band selection**: drag empty area to select multiple nodes
- Right-click selection → Save / Delete / **Duplicate** selection
- Duplicate: copies nodes offset 40px, re-wires internal arrows, selects copies
- **Multi-drag**: moving any selected node moves all selected nodes together
- Right-click Add Node menu excludes: CLICK, CONDITION, LOOP, STOP
- Icon font size 17 for KEYBOARD, SIMPLE_CLICK, MESSAGE (others 13)

### NodeEditor.java (src/ui/)
- IMAGE node: threshold spinner removed (threshold only in Watch Case)
- IMAGE node: rename updates connected arrow labels live
- MESSAGE node: style picker, content, timing, size, position picker, 3 color pickers
- `addColorPicker()` — swatch opens JColorChooser
- `showPositionPicker()` — crosshair overlay to set message position
- `getIntField()`/`setIntField()` handle enum fields via ordinal
- Watch Case threshold labeled "Match %"

### NodeFactory.java (src/nodes/)
- STOP removed, MESSAGE added (amber color, ✉ icon `\u2709`)
- MessageNode: Toast (style=0) or Floating Window (style=1), customizable colors, auto-dismiss, wait-for-dismiss option, center or custom position
- MessageNode.execute(): renders JWindow, CountDownLatch for pause/wait

### WatchCaseNode.java (src/nodes/)
- **Fixed**: first match wins per zone — stops checking other images once one matches
- No longer clicks multiple times when multiple images match same zone
- HUD shows zone → image + match % with ✓ / "no match" feedback

### NodePalette.java (src/ui/)
- STOP replaced with MESSAGE
- Bigger icons for KEYBOARD, SIMPLE_CLICK, MESSAGE (font 20)
- Watch Case icon: `\u2756` (✦)

### SimpleClickPanel.java (src/ui/)
- Mouse widget (56×48px): left=blue, right=red, scroll=green, bottom=orange
- Table: 7 columns {#, X, Y, Clicks, Sub-delay, After-delay, Type}
- Drag pairs: orange tint, ◆/↳ indicators, right-click removes pair

### TreeTab.java (src/ui/)
- Toolbar order: Run, Loop, Stop, Architect, AI Learn, AI Chat | Save, Load | Fit View, Clear | Zoom-, Zoom+, 100% | Event Log
- **⏺ AI Learn**: records actions, sends to AI, applies generated node tree
- **✦ AI Chat**: persistent chat panel, maintains conversation history, applies JSON replies to canvas
- **⬡ Event Log**: timestamped node execution log panel
- Analysis progress dialog during AI processing
- `applyAIResult()`: strips markdown, loads JSON, opens chat with welcome message
- Task name label removed from toolbar

### TurboClick.java
- Settings button in sidebar → SettingsPanel
- Active tab: `new Color(40,40,62)` bg + blue bottom border via `getClientProperty("linkedTab")`
- Tab headers: BorderLayout (dot+name left, × right)
- Add tab button: ＋, fixed 42×38

---

## Key Technical Decisions
- macOS JWindow: `setFocusableWindowState(false)` on all overlays
- macOS pass-through: `Window.Type.POPUP` + `setEnabled(false)`
- macOS clip leak: always `g2.create()` / `g2.dispose()`
- Live drag: `MouseInfo.getPointerInfo()` not `e.getX()`
- ESC in overlays: jNativeHook GlobalScreen
- TaskSerializer: hand-rolled JSON — no external dependency
- AI: hand-rolled HTTP to Anthropic API — no SDK
- API key: `~/.turboclick/config.properties`
- Enum fields in NodeEditor handled via `.ordinal()` reflection

---

## Node Types (active)
WATCH_ZONE, SIMPLE_CLICK, WAIT, MESSAGE, KEYBOARD, IMAGE, WATCH_CASE
(CLICK, CONDITION, LOOP excluded from right-click palette; still in codebase)

## Port Logic
- Done / Found → green
- Not Found → red
- Timeout → yellow
- WatchCase: image inputs left, case outputs right, Done/NoneFound bottom
- MESSAGE: single Done output

## WatchCase Matching Logic
- Iterates each zone in order
- Per zone: capture screenshot → check each image → **first match wins, break**
- Clicks based on zone click mode (At Match / Center / Custom Pin / None)
- Returns Done if any zone matched, None Found if nothing matched
- loopOnMatch: cycles all zones until nothing found anywhere

---

## Future Plans
1. ~~Save/load~~ ✅
2. ~~DMG installer~~ ✅ `./build.sh package`
3. ~~Event log / error messages~~ ✅
4. ~~AI Record & Learn~~ ✅ AI Learn + AI Chat
5. **AI Create from description** — skip recording, just describe the task in chat
6. **Scheduling** — run at specific time (Wait Until Time partially covers this)
7. **Cross-tab AI** — AI sees all tasks, can create new tabs