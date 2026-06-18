const { WebSocketServer } = require('ws');
const readline = require('readline');

const PORT = process.env.PORT || 8080;
const CMD_PORT = process.env.CMD_PORT || 8081;
const wss = new WebSocketServer({ port: PORT });

let client = null;
const cmdClients = [];

console.log(`\n  PhoneController Test Server`);
console.log(`  Listening on ws://0.0.0.0:${PORT}`);
console.log(`  Type or paste JSON commands below.\n`);

function broadcastResponse(data) {
  const text = typeof data === 'string' ? data : data.toString();
  for (const ws of cmdClients) {
    if (ws.readyState === 1) ws.send(text);
  }
}

wss.on('connection', (ws, req) => {
  client = ws;
  const addr = req.socket.remoteAddress;
  console.log(`  Phone connected from ${addr}`);

  ws.on('message', (data) => {
    const text = data.toString();
    try {
      const parsed = JSON.parse(text);
      const status = parsed.ok ? 'OK' : 'ERROR';
      console.log(`\n  Response [${status}]:`);
      console.log(`  ${JSON.stringify(parsed, null, 2)}\n`);
    } catch {
      console.log(`\n  Raw response: ${text}\n`);
    }
    broadcastResponse(text);
  });

  ws.on('close', () => {
    console.log('  Phone disconnected');
    client = null;
  });

  ws.on('error', (err) => {
    console.error('  Phone WebSocket error:', err.message);
    client = null;
  });
});

// Command relay server - scripts connect here to send commands
const cmdServer = new WebSocketServer({ port: CMD_PORT });
cmdServer.on('connection', (ws) => {
  cmdClients.push(ws);
  console.log(`  Command client connected on port ${CMD_PORT}`);
  ws.on('message', (data) => {
    const text = data.toString();
    console.log(`\n  Command forwarded: ${text}\n`);
    if (client && client.readyState === 1) {
      client.send(text);
    } else {
      ws.send(JSON.stringify({ ok: false, error: 'phone_not_connected' }));
    }
  });
  ws.on('close', () => {
    const idx = cmdClients.indexOf(ws);
    if (idx >= 0) cmdClients.splice(idx, 1);
    console.log('  Command client disconnected');
  });
});
console.log(`  Command relay on ws://0.0.0.0:${CMD_PORT}`);

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  prompt: 'cmd> '
});

rl.prompt();

rl.on('line', (line) => {
  const trimmed = line.trim();
  if (!trimmed) {
    rl.prompt();
    return;
  }

  // Predefined shortcuts
  let json = trimmed;
  const shortcuts = {
    'home':       '{"id":"1","type":"home"}',
    'back':       '{"id":"2","type":"back"}',
    'tap center': '{"id":"3","type":"tap","x":500,"y":1000}',
    'swipe up':   '{"id":"4","type":"swipe","x1":500,"y1":1600,"x2":500,"y2":400,"duration":500}',
    'swipe down': '{"id":"4","type":"swipe","x1":500,"y1":400,"x2":500,"y2":1600,"duration":500}',
    'ui tree':    '{"id":"5","type":"get_ui_tree"}',
    'get_ui_tree':'{"id":"5","type":"get_ui_tree"}',
    'click_text': '{"id":"6","type":"click_text","text":"Search"}',
    'click_desc': '{"id":"7","type":"click_description","description":"Search"}',
  };

  if (shortcuts[trimmed.toLowerCase()]) {
    json = shortcuts[trimmed.toLowerCase()];
    console.log(`  Shortcut -> ${json}`);
  }

  if (client && client.readyState === 1) {
    client.send(json);
    console.log(`  Sent to client\n`);
  } else {
    console.log('  No client connected\n');
  }

  rl.prompt();
});

process.on('SIGINT', () => {
  console.log('\nShutting down...');
  wss.close(() => process.exit(0));
});

process.on('SIGTERM', () => {
  wss.close(() => process.exit(0));
});
