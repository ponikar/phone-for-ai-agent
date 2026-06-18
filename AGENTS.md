# AI Agent Guide

This project lets AI agents control an Android phone. Read `AI_SKILL.md` for complete instructions.

## Quick Reference

```bash
# Start server (laptop)
cd server && npm start

# Send command (port 8081 on same laptop)
node server/send.js '{"id":"1","type":"get_ui_tree"}'

# Available types:
#   tap, swipe, back, home, get_ui_tree, click_text, click_description
#   type, wait, long_press, keyevent, get_state
```

## How Connection Works

```
┌──────────────────┐   WiFi (same network)   ┌──────────────┐
│  Laptop (server) │ ◄──────────────────────► │  Phone (app) │
│  ws://:8080      │                          │  connects to │
│  ws://:8081      │                          │  ws://IP:8080│
└────────┬─────────┘                          └──────┬───────┘
         │                                           │
    You send commands                         Phone executes
    via send.js to 8081                       taps/swipes/etc
```

## Key Networking Details

The communication happens over **local WiFi only** using the laptop's **local IP address** (starts with 192.168., 10., or 172.).

- **Find it:** `ifconfig | grep "inet " | grep -v 127.0.0.1`
- **Example:** `192.168.1.102`
- **Phone enters:** `ws://192.168.1.102:8080` in the app
- **Commands go to:** port 8081 on the same machine

Both devices must be on the same WiFi. The phone cannot reach `localhost` or `127.0.0.1` on your laptop. Public IPs won't work for local WiFi connections.

## Typical Workflow

1. `get_ui_tree` → see what's on screen
2. `click_text "YouTube"` or `click_description "Search"`
3. `type "query"` → type into focused field
4. `wait 2000` → let animations settle
5. `get_ui_tree` → see results
6. `tap` or `click_text` → select target
7. Repeat

## Key Files

- `AI_SKILL.md` — Full skill documentation (read this first)
- `server/server.js` — WebSocket server (port 8080 phone, 8081 commands)
- `server/send.js` — Command helper script
- `app/` — Android app source code
