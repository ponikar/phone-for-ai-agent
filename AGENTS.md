# AI Agent Guide

This project lets AI agents control an Android phone. Read `AI_SKILL.md` for complete instructions on:

## Quick Reference

```bash
# Start server
cd server && npm start

# Send command (port 8081)
node server/send.js '{"id":"1","type":"get_ui_tree"}'

# Available types: tap, swipe, back, home, get_ui_tree, click_text, click_description
```

## Key Files

- `AI_SKILL.md` — Full skill documentation (read this first)
- `server/server.js` — WebSocket server (port 8080 phone, 8081 commands)
- `server/send.js` — Command helper script
- `app/` — Android app source code

## Workflow

1. Start server → phone connects to port 8080
2. `get_ui_tree` to see what's on screen
3. Send tap/swipe/click commands based on UI tree
4. `get_ui_tree` again to verify the result
5. Repeat
