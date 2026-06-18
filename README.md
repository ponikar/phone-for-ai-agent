# Phone for AI Agent

> **Experimental** — Give your AI agent direct access to your Android phone, just like a human would use it.

This is a native Android app + Node.js server that lets an AI agent (or any laptop-based script) control a phone through WebSocket commands — tapping, swiping, navigating, reading the UI, and more.

Instead of brittle automation frameworks or screen scraping, it uses Android's **AccessibilityService** to interact with apps the same way a human does: by reading the UI tree and dispatching real gestures.

## How it Works

```
┌──────────────┐    WebSocket     ┌───────────────┐
│  Phone App   │ ◄──────────────► │  Server/Laptop│
│  (Android)   │                  │  (Node.js)    │
└──────────────┘                  └───────────────┘
     │                                    │
     │ AccessibilityService               │ Command scripts
     │ (tap, swipe, back, home,           │ (port 8081)
     │  get UI tree, click by text)       │
     │                                    │
     └── Real human-like interaction ─────┘
```

## What It Can Do

- **Tap** anywhere by coordinates
- **Swipe** in any direction
- **Navigate** (back, home)
- **Read the screen** — get the full UI node tree (text, descriptions, bounds, clickable, etc.)
- **Find and click** by visible text or content description
- **Run in background** via foreground service
- **Two-port server** — 8080 for the phone, 8081 for command scripts, with response forwarding

## Why?

AI agents need to interact with real apps, not just APIs. This project gives them a phone. You can:

- Ask your AI to "open YouTube and play a song"
- Have it scroll through Instagram, tap posts, and read comments
- Automate any app with natural language commands
- Let your coding assistant test your mobile app by actually using it

## Setup

### 1. Build & Install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Enable Accessibility

1. Open the app → tap **Open Accessibility Settings**
2. Find **PhoneController** in the list
3. Toggle it ON and confirm

### 3. Start the Server

```bash
cd server
npm install
npm start
```

### 4. Connect

1. Find your IP: `ifconfig | grep inet`
2. Enter in the app: `ws://<YOUR_IP>:8080`
3. Tap **Connect**

### 5. Send Commands

In the server terminal, type shorthand commands:

```
home
back
tap center
swipe up
ui tree
```

Or send raw JSON via the command relay (port 8081):

```bash
node send.js '{"id":"1","type":"tap","x":500,"y":1200}'
```

## Supported Commands

| Type | Params | Description |
|---|---|---|
| `tap` | `x`, `y` | Tap at screen coordinates |
| `swipe` | `x1`, `y1`, `x2`, `y2`, `duration` | Swipe gesture |
| `back` | — | Trigger back button |
| `home` | — | Go to home screen |
| `get_ui_tree` | — | Dump current screen's node hierarchy |
| `click_text` | `text` | Click the first node matching visible text |
| `click_description` | `description` | Click the first node matching content description |

## Project Structure

```
├── app/src/main/java/com/phonecontroller/
│   ├── MainActivity.kt              # UI, connection, and service control
│   ├── PhoneAccessibilityService.kt # Gesture dispatch, UI tree walking
│   ├── AccessibilityController.kt   # Singleton service reference
│   ├── WebSocketManager.kt          # OkHttp WebSocket client
│   ├── WebSocketService.kt          # Foreground service for persistence
│   ├── CommandParser.kt             # JSON command validation
│   └── models/                      # Data classes (CommandWrapper, Response, UiNode)
├── server/
│   ├── server.js                    # Dual-port WebSocket server
│   └── send.js                      # Command helper script
└── build.gradle.kts
```

## Safety

- Commands only execute while Accessibility Service is enabled
- Only 7 defined command types are accepted — no arbitrary execution
- All gestures and actions are logged
- Foreground service ensures the connection stays alive

## License

MIT
