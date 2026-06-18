# PhoneController

Native Android app that allows a laptop/server AI agent to control the phone via WebSocket commands.

## Architecture

```
┌──────────────┐    WebSocket     ┌───────────────┐
│  Phone App   │ ◄──────────────► │  Server/Laptop│
│  (Android)   │                  │  (Node.js)    │
└──────────────┘                  └───────────────┘
     │                                    │
     │ AccessibilityService               │
     │ (tap, swipe, back, home,           │
     │  get UI tree, click by text)       │
```

## Setup

### 1. Build & Install

```bash
# Build the APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Enable Accessibility

1. Open the app
2. Tap **Open Accessibility Settings**
3. Find "PhoneController" in the installed services
4. Toggle it ON
5. Confirm the dialog

### 3. Start the Test Server

```bash
cd server
npm install
npm start
```

### 4. Connect

1. Find your laptop's IP: `ifconfig | grep inet`
2. Enter in the app: `ws://<YOUR_IP>:8080`
3. Tap **Connect to Server**
4. Status should show **Connected** (green)

### 5. Send Commands

In the server terminal, type commands:

```text
home
back
tap center
swipe up
ui tree
```

Or paste raw JSON:

```json
{"id":"1","type":"tap","x":500,"y":1200}
```

## Supported Commands

| Type | Params | Description |
|---|---|---|
| `tap` | `x`, `y` | Tap coordinates |
| `swipe` | `x1`, `y1`, `x2`, `y2`, `duration` | Swipe gesture |
| `back` | — | Global back action |
| `home` | — | Global home action |
| `get_ui_tree` | — | Get current screen node tree |
| `click_text` | `text` | Click first node matching text |
| `click_description` | `description` | Click first node matching content description |

## Project Structure

```
app/src/main/java/com/phonecontroller/
├── MainActivity.kt              # UI & connection management
├── PhoneAccessibilityService.kt # Gesture dispatch & UI tree
├── AccessibilityController.kt   # Singleton service reference
├── WebSocketManager.kt          # OkHttp WebSocket client
├── CommandParser.kt             # JSON command validation
└── models/
    ├── CommandWrapper.kt
    ├── CommandResponse.kt
    └── UiNode.kt
```

## Safety

- Commands only execute when Accessibility Service is enabled
- Only the 7 defined command types are accepted
- No arbitrary shell execution
- All gestures are logged
