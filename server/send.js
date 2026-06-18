const WebSocket = require('ws');

// Connect to the running server, send a command, read response
const ws = new WebSocket('ws://localhost:8081');
const cmd = process.argv[2];

ws.on('open', () => {
  ws.send(cmd);
});

ws.on('message', (data) => {
  console.log(JSON.parse(data.toString()));
  ws.close();
  process.exit(0);
});

ws.on('error', (e) => {
  console.error('Error:', e.message);
  process.exit(1);
});

setTimeout(() => { process.exit(1); }, 5000);
