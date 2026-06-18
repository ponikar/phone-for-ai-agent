# AI Skill: Phone Controller

> ⚠️ **WARNING:** This is an experimental project. It gives remote control of your phone over the network. Use it **only on trusted local WiFi networks**. The authors take **no responsibility** for any data loss, privacy breaches, unintended actions, or misuse. Sensitive information (passwords, messages, photos, banking details, etc.) may be exposed to the AI agent or the network. Use at your own risk. Disable the Accessibility Service and uninstall the app when not in use.

This skill teaches an AI agent how to control an Android phone using the PhoneController app. You can tap, swipe, navigate, read the screen, and find+click elements — all through WebSocket commands.

## Prerequisites

- PhoneController APK installed on the Android device
- Accessibility Service enabled for PhoneController
- Phone and laptop on the **same WiFi network** (this is essential)
- Node.js installed on the laptop

---

## 1. Start the Server

```bash
cd server
npm install   # first time only
npm start
```

This starts two WebSocket servers:
- **Port 8080** — the phone connects here
- **Port 8081** — you send commands here (via the helper script)

The phone must be connected to port 8080 before any commands work. The server logs show when a phone connects.

---

## 2. Network Setup: Finding the IP Address

The laptop runs a WebSocket server. The phone connects to it over your local WiFi. This means you need the laptop's **local IP address** on the WiFi network — NOT a public IP, NOT 127.0.0.1.

### How to find the laptop's local IP

**macOS / Linux:**
```bash
# Look for the IP next to "inet" — usually starts with 192.168., 10., or 172.
ifconfig | grep "inet " | grep -v 127.0.0.1

# Example output:  inet 192.168.1.102 netmask 0xffffff00 broadcast 192.168.1.255
# Your IP is: 192.168.1.102
```

**Windows (PowerShell):**
```powershell
ipconfig | findstr /i "IPv4"
```

### How to connect the phone

1. Open the **PhoneController** app on your phone
2. Tap the URL input field at the top
3. Type: `ws://192.168.1.102:8080` (replace with *your* laptop IP)
4. Tap **Connect** button

The status indicator should turn **green** and show "Connected". The server terminal will print:
```
  Phone connected from 192.168.1.104
```

### Troubleshooting connection issues

| Problem | Likely fix |
|---|---|
| Phone can't connect | Both devices must be on **the same WiFi network** |
| Connection times out | Check laptop firewall — port 8080 needs to be open |
| "Connection failed" on phone | Make sure the server is running first (`npm start`) |
| IP keeps changing | Set a static IP on your laptop, or check IP each session |
| Using mobile hotspot | Hotspot creates its own network. Phone and laptop should both be on it. Find the laptop's IP from `ifconfig` while connected to the hotspot |

### Important networking notes

- **Local IPs** (192.168.x.x, 10.x.x.x, 172.16-31.x.x) only work within your home/office WiFi
- **Do not use 127.0.0.1 or localhost** — the phone can't reach those from another device
- **Do not use your public IP** — that's your router's internet address, and the phone is inside your network
- IP addresses can change when you restart your laptop or reconnect to WiFi. Always re-check with `ifconfig` before starting a session
- The protocol is `ws://` (not `wss://`). The app has cleartext traffic enabled for local network use

---

## 3. Sending Commands

Use the `send.js` helper to send JSON commands to port 8081:

```bash
node server/send.js '{"id":"1","type":"tap","x":500,"y":1000}'
```

`id` can be any string — use it to correlate requests with responses. The response is printed to stdout.

**Always wait for a response before sending the next command.** Commands execute on the phone's main thread and are processed sequentially.

---

## 4. Available Commands

### 4.1 `tap` — Tap at coordinates

```json
{"id":"1","type":"tap","x":500,"y":1200}
```

Response: `{"id":"1","ok":true,"message":"tap executed at (500, 1200)"}`

### 4.2 `swipe` — Swipe gesture

```json
{"id":"2","type":"swipe","x1":500,"y1":1600,"x2":500,"y2":400,"duration":500}
```

`duration` is in milliseconds (default: 500). Useful for scrolling, pulling down notifications, etc.

Response: `{"id":"2","ok":true,"message":"swipe executed (500,1600)->(500,400)"}`

### 4.3 `back` — Go back

```json
{"id":"3","type":"back"}
```

Triggers the system back button.

### 4.4 `home` — Go home

```json
{"id":"4","type":"home"}
```

Triggers the system home button.

### 4.5 `get_ui_tree` — Read the screen

```json
{"id":"5","type":"get_ui_tree"}
```

Returns all visible nodes on screen (up to 300):

```json
{
  "id":"5",
  "ok":true,
  "message":"ui_tree_returned",
  "nodes":[
    {
      "text": "Search YouTube",
      "description": "Search",
      "viewId": "com.google.android.youtube:id/search_edit_text",
      "className": "android.widget.EditText",
      "packageName": "com.google.android.youtube",
      "clickable": true,
      "enabled": true,
      "focused": true,
      "bounds": [50, 100, 700, 150]
    }
  ]
}
```

Each node has these fields:

| Field | Type | Description |
|---|---|---|
| `text` | string or null | Visible text on the element |
| `description` | string or null | Content description (accessibility label) |
| `viewId` | string or null | Android resource ID (e.g., `com.app:id/button`) |
| `className` | string or null | Android class name (e.g., `android.widget.Button`) |
| `clickable` | boolean | Whether this element can be clicked |
| `enabled` | boolean | Whether this element is enabled |
| `focused` | boolean | Whether this element has keyboard focus |
| `bounds` | [left, top, right, bottom] | Screen coordinates in pixels |

**`get_ui_tree` is your primary way to understand what's on the screen.** Use it frequently to navigate.

### 4.6 `click_text` — Click by visible text

```json
{"id":"6","type":"click_text","text":"Search"}
```

Finds the first clickable node whose `text` **contains** the query (case-insensitive). If the matched node is not clickable, it walks up to find a clickable parent.

Response: `{"id":"6","ok":true,"message":"clicked text: Search"}`
Error: `{"id":"6","ok":false,"message":"no clickable node found with text: Search"}`

### 4.7 `click_description` — Click by content description

```json
{"id":"7","type":"click_description","description":"Search"}
```

Same as `click_text` but matches against `contentDescription`. Use this for icons and image buttons that don't have visible text.

---

## 5. Reading the Screen

### Typical `get_ui_tree` analysis workflow:

1. **Send `get_ui_tree`** to see what's on screen
2. **Look at the `nodes` array** — each entry is a UI element
3. **Identify the target element** by its `text`, `description`, or `viewId`
4. **Check if it's clickable** — if not, find a parent that is
5. **Use coordinates for precision** — `bounds` gives you exact pixel positions
6. **Use `click_text`/`click_description`** for convenience when text is unique

### Screen dimension inference:

The bottom-most `bounds` values give you the screen height. Typical resolutions:
- `bounds: [0, 0, 720, 1600]` — common budget phone (hdpi)
- `bounds: [0, 0, 1080, 2400]` — typical modern phone (xxhdpi)

### Finding elements strategy:

- **Buttons with text** → use `click_text` with the button label
- **Icons/ImageButtons** → use `click_description` with the content description
- **Exact position** → read `bounds` from the UI tree and use `tap`
- **Scrollable areas** → look for `className: "android.widget.ScrollView"` or `"android.support.v7.widget.RecyclerView"`
- **Search fields** → often have `focused: true` when active
- **Bottom navigation** → look for pivot bars or tab bars at the bottom of the screen

---

## 6. Response Format

All responses follow this schema:

```json
{
  "id": "string (matches the request id)",
  "ok": true,
  "message": "human-readable status",
  "error": null,
  "nodes": null
}
```

On error:
```json
{
  "id": "error",
  "ok": false,
  "error": "error_code"
}
```

Common error codes:
- `accessibility_service_not_enabled` — user hasn't enabled the service
- `unknown_command_type` — the `type` field is invalid
- `tap requires x/y` — missing required parameters
- `no clickable node found with text: ...` — text not found on screen
- `no clickable node found with description: ...` — description not found on screen
- `phone_not_connected` — no phone connected to the server

---

## 7. Standard Interaction Patterns

### Pattern A: Open an app and do something

```
1. get_ui_tree → see home screen
2. If app not visible, swipe to find it or use home first
3. click_text "YouTube" → opens YouTube
4. get_ui_tree → see YouTube home
5. click_description "Search" → activates search
6. get_ui_tree → see search field is focused
7. (type text via ADB: input text "beauty and the beach")
8. get_ui_tree → see search results
9. click_text or click_description → select a result
```

### Pattern B: Navigate an app

```
1. get_ui_tree → understand current screen
2. click_text "Settings" or click_description "More options"
3. get_ui_tree → verify navigation worked
4. Repeat
```

### Pattern C: Handle dialogs

```
1. get_ui_tree → look for dialog buttons (OK, Cancel, Allow, etc.)
2. click_text "Allow" or click_text "OK"
3. get_ui_tree → confirm dialog is gone
```

### Pattern D: Scroll through content

```
1. get_ui_tree → note what's visible
2. swipe down (or up) to scroll
   {"id":"8","type":"swipe","x1":360,"y1":1400,"x2":360,"y2":400,"duration":500}
3. get_ui_tree → see new content
4. Repeat as needed
```

### Pattern E: Typing text

The app doesn't have a built-in "type" command. Use ADB as a fallback when the target text field is focused:

```bash
adb shell input text "your text here"
adb shell input keyevent 66  # Enter key
```

---

## 8. Tips

- **Always `get_ui_tree` before acting** — you need to know the current screen state
- **Wait 1-3 seconds between commands** for animations to settle
- **Keep the screen on** during automation (disable screen timeout or use ADB: `adb shell svc power stayon true`)
- **Use unique identifiers** — `click_text` with common words like "OK" might match the wrong element. Prefer longer, more specific strings
- **Coordinate system** — (0,0) is top-left. x increases right, y increases down
- **The `get_ui_tree` is limited to 300 nodes** — very complex screens may be truncated
- **`swipe` coordinates are start→end**, so `swipe up` goes from low y to high y (wait — actually the Y axis increases downward, so swipe up would be y1 > y2, e.g., y1:1600 → y2:400)

---

## 9. Typical Startup Sequence

```bash
# 1. Start the server
cd /path/to/phone-for-ai-agent/server && npm start

# 2. Verify phone is connected (wait for "Phone connected" in server logs)

# 3. Test the connection
node send.js '{"id":"test","type":"get_ui_tree"}'

# 4. Start controlling
node send.js '{"id":"1","type":"home"}'
node send.js '{"id":"2","type":"get_ui_tree"}'
node send.js '{"id":"3","type":"click_text","text":"YouTube"}'
```
