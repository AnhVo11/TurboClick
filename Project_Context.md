# TurboClick — Full Project Summary

## What It Is
A Java macOS desktop automation app with two modes:
1. **Build Smart Click** — visual node canvas to build automation flows
2. **Build Simple Click** — auto-clicker with per-point click types

## Build Command
```bash
chmod +x build.sh && ./build.sh
# Requires: lib/jnativehook-2.2.2.jar
```

## File Structure
```
src/
  engine/   ExecutionContext.java, RuleEngine.java, SimpleClickEngine.java
  nodes/    BaseNode.java, NodeFactory.java, ImageNode.java, WatchCaseNode.java
  ui/       NodeCanvas.java, NodeEditor.java, NodePalette.java,
            SimpleClickPanel.java, TaskArchitectOverlay.java, TreeTab.java
SaveFormat.java
TaskSerializer.java
build.sh
lib/jnativehook-2.2.2.jar
```

---

## What's Been Built & Changed

### BaseNode.java (src/nodes/)
- Added `public int nodeLoopCount = 1`
- `runState` is public volatile
- Added `isRunStateExpired(long ms)` — tracks when state last changed
- Added `inputs` list (public)
- Added geometry methods:
  - `bounds()` — Rectangle for hit testing
  - `inputAnchor()` — top-center point
  - `outputAnchor(String)` — bottom port points
  - `leftInputAnchor(String)` — left side for WatchCase
  - `rightOutputAnchor(String)` — right side for WatchCase
- Added `setPortTarget(String, String)`
- Added `addInputPort(String)`
- NodePort has two-arg constructor `NodePort(name, targetNodeId)`

### RuleEngine.java (src/engine/)
- Wraps each node execution in loop up to `nodeLoopCount` times
- Early exit if ANY iteration succeeds (non "Not Found" result)
- Only follows "Not Found" port if ALL iterations fail
- null portResult (Stop node) always = success

### SimpleClickEngine.java (src/engine/)
- Points array is 6 elements: `{x, y, clicks, subDelayMs, afterDelayMs, btnType}`
- btnType: 0=Left, 1=Right, 2=Middle, 3=Drag
- Type 3 (Drag): mousePress → smooth 20-step move → mouseRelease
- `btnMaskForPoint()` reads per-point type, falls back to global

### SimpleClickPanel.java (src/ui/)
- Mouse widget (56×48px): left=blue, right=red, scroll=green, bottom=orange(drag)
- `currentPinType` persists across pins until changed
- Table: 7 columns {#, X, Y, Clicks, Sub-delay, After-delay, Type}
- Type stored as raw int, renderer shows colored L/R/M/D badge
- Drag pair rows: orange tint background (42,35,22)
- # column shows ◆N (start) / ↳N (end) for drag pairs
- Right-click any drag handle → removes entire pair with warning dialog
- Remove button warns before deleting drag pair ("Don't show again" checkbox)
- DragPin: two JWindows (startWin, endWin) + midWin diamond
- dragLineOverlay: full-screen POPUP window, draws orange dashed line + arrowhead
- All handles: setFocusableWindowState(false)

### NodeEditor.java (src/ui/)
- Removed ring color picker entirely from Simple Click node panel
- Table: 7 columns {#, X, Y, Clicks, Sub-delay, After-delay, Type}
- Drag rows: orange tint (42,35,22), ◆/↳ indicators in # column
- Right-click drag row in table → warns + deletes both rows
- Type column: colored L/R/M/D badge, click to cycle (not for drag rows)
- EditorPin: type-colored rings, no badge, hover=red dot
- DragEditorPin: two handles + midpoint diamond + dragLineOverlay
- Smart Pin HUD: mouse widget (56×48), sessionPinType persists
- buildPointsList includes 6th element (btnType)

### NodeCanvas.java (src/ui/)
- Loop badge (↺ N) drawn at top-right corner of every node
- Badge is subtle/gray when count=1, gold pill when count>1
- Single left-click on badge opens spinner popup to set nodeLoopCount
- Popup closes on focus loss, updates node live while spinning
- loopBadgeRect() / loopBadgeHit() helper methods

### TreeTab.java (src/ui/)
- Run, Loop, Stop buttons in toolbar
- Loop button: repeats entire tree until stopped
- Run HUD: floating status bar with node name + stop button
- findStartNode() — finds node with no incoming connections
- Save / Load buttons wired to TaskSerializer (file chooser, .json extension)
- "Save Selection" saves only selected nodes + internal arrows

### TaskArchitectOverlay.java (src/ui/) — NEW
- Full-screen transparent JWindow overlay launched from TreeTab toolbar
- Shows every Watch Zone rect and every click pin (Click, Simple Click, Watch Case) overlaid on the live screen
- Two item types:
  - **RectItem** — dashed border rect with label badge + size hint; 8 resize handles when selected
  - **PinItem** — crosshair + color-coded ring (blue=L, red=R, green=M, orange=drag); number badge
- Drag to reposition any rect or pin; resize rects via corner/edge handles
- Changes write back to the live node via stored Runnable callbacks (no reflection at drag time)
- HUD bar (top of screen): node name + item label while hovering; hint text; ✓ Done button
- Right-click any item → confirm dialog → removes/clears that zone or pin from its node
- ESC closes overlay via jNativeHook GlobalScreen listener
- Dashed orange arrow lines drawn between consecutive drag-pair pins
- Hit testing: pins take priority over rects; last-drawn item wins within same type
- `buildItems()` uses reflection once at open time to read all node fields into ArchItem wrappers
- `drawDragLines()` connects drag-start → drag-end pin pairs with arrowhead

### SaveFormat.java — NEW
- Plain data-class container (no logic); hand-rolled, no external JSON library
- `TaskFile`: version, appVersion, savedAt, taskName, startNodeId, nodes[], arrows[]
- `NodeData`: all node fields as nullable boxed types (Integer/Boolean/Long); images as base64 String
- `WatchCaseData`: portName, threshold, hasOutput, imageNodeId
- `WatchZoneData`: name, rect int[4], clickMode, customPinX/Y, clickDelayMs
- `ArrowData`: fromNodeId, fromPort, toNodeId, label, bendOffset
- Current VERSION = 1

### TaskSerializer.java — NEW
- `saveTask(taskName, startNodeId, nodeMap, arrows, file)` — full canvas save
- `saveNodes(taskName, selectedNodes, allArrows, nodeMap, file)` — save selection only (arrows filtered to both-ends-in-set)
- `load(file)` → `SaveFormat.TaskFile` — reads JSON from disk
- `applyToCanvas(tf, canvas, offsetX, offsetY)` → remapped startNodeId — adds nodes + re-wires arrows with fresh UUIDs
- `nodeToData()` / `dataToNode()` — per-type field serialization via reflection helpers
- Images serialized as base64 PNG via `imgToBase64` / `base64ToImg`
- Hand-rolled JSON writer (`writeJson`, `nodeDataToJson`) and minimal recursive parser (`parseJson`, `splitObjects`, `arrayBlock`)
- Reflection helpers: `field()`, `getF()`, `setF()`, `setI()`, `setB()`, `str()`, `intF()`, `boolF()`, `img()`, `rect()`
- Save/load has NOT been fully tested yet

---

## Key Technical Decisions
- macOS JWindow focus: `setFocusableWindowState(false)` on all overlays
- macOS mouse pass-through: `Window.Type.POPUP` + `setEnabled(false)`
- macOS clip leak: always `g2.create()` / `g2.dispose()` in paint methods
- Live drag coords: `MouseInfo.getPointerInfo()` not `e.getX()`
- Type as raw int in table model — getValueAt only converts delay cols (4,5)
- ESC in overlays: jNativeHook GlobalScreen listener
- macOS buttons: JLabel with border instead of JButton for custom colors
- TaskArchitectOverlay writes back to nodes via Runnable callbacks captured at open time (avoids repeated reflection during drag)
- TaskSerializer uses hand-rolled JSON (no Gson/Jackson dependency) — keeps build simple

---

## Node Types
WATCH_ZONE, CLICK, SIMPLE_CLICK, CONDITION, LOOP, WAIT, STOP, KEYBOARD, IMAGE, WATCH_CASE

## Port Logic
- "Found" / "Done" → green → success path
- "Not Found" → red → failure path
- "Timeout" → yellow
- Ports evenly spaced on bottom edge of node
- WatchCase: image inputs on left, case outputs on right, Done/NoneFound on bottom

---

## Future Plans (not built yet)
1. ~~Save/load node trees to disk~~ ✅ Built (untested)
2. Scheduling ("run at 9am daily")
3. Simple .dmg installer
4. Better error messages
5. **AI "Record & Learn"** — user describes task, does it manually,
   Claude Vision API watches screenshots + clicks, auto-generates node tree
   - RecordingEngine.java: capture clicks + screenshots via jNativeHook
   - AIAnalyzer.java: send to Claude API, parse JSON response → BaseNodes
   - New "Record & Learn" button in TreeTab toolbar