import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import WebSocket from 'ws';

const base = process.env.TEST_BASE_URL || 'http://127.0.0.1:3000';
const wsBase = base.replace(/^http/, 'ws');
const stamp = Date.now().toString().slice(-6);
const alice = `alice_${stamp}`;
const bob = `bob_${stamp}`;

async function request(path, options = {}) {
  const response = await fetch(base + path, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  return { response, data };
}
async function waitForServer() {
  for (let i = 0; i < 30; i++) {
    try { const { response } = await request('/health'); if (response.ok) return; } catch {}
    await delay(500);
  }
  throw new Error('Server did not become healthy');
}
function connect(token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${wsBase}/ws?token=${encodeURIComponent(token)}`);
    const timer = setTimeout(() => { ws.close(); reject(new Error('WebSocket connect timeout')); }, 5000);
    ws.once('open', () => { clearTimeout(timer); resolve(ws); });
    ws.once('error', error => { clearTimeout(timer); reject(error); });
  });
}
function nextMessage(ws, timeout = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket message timeout')), timeout);
    ws.once('message', data => { clearTimeout(timer); resolve(JSON.parse(data.toString())); });
  });
}

await waitForServer();

const invalid = await request('/api/me', { headers: { Authorization: 'Bearer definitely-invalid' } });
assert.equal(invalid.response.status, 401, 'invalid auth must return 401');

const a = await request('/api/auth/register', { method: 'POST', body: JSON.stringify({ username: alice, password: 'StrongPass123!' }) });
const b = await request('/api/auth/register', { method: 'POST', body: JSON.stringify({ username: bob, password: 'StrongPass123!' }) });
assert.equal(a.response.status, 201);
assert.equal(b.response.status, 201);

const duplicate = await request('/api/auth/register', { method: 'POST', body: JSON.stringify({ username: alice, password: 'StrongPass123!' }) });
assert.equal(duplicate.response.status, 409, 'duplicate usernames must be rejected');

const wrong = await request('/api/auth/login', { method: 'POST', body: JSON.stringify({ username: alice, password: 'WrongPass123!' }) });
assert.equal(wrong.response.status, 401, 'wrong password must be rejected');

const aliceWs = await connect(a.data.token);
const bobWs = await connect(b.data.token);

const incomingAtBob = nextMessage(bobWs);
const sent = await request('/api/messages', { method: 'POST', headers: { Authorization: `Bearer ${a.data.token}` }, body: JSON.stringify({ recipient: bob, body: 'integration-test-message' }) });
assert.equal(sent.response.status, 201);
const bobMessage = await incomingAtBob;
assert.equal(bobMessage.type, 'message');
assert.equal(bobMessage.sender, alice);
assert.equal(bobMessage.recipient, bob);
assert.equal(bobMessage.body, 'integration-test-message');

const history = await request(`/api/messages/${encodeURIComponent(alice)}`, { headers: { Authorization: `Bearer ${b.data.token}` } });
assert.equal(history.response.status, 200);
assert.ok(history.data.messages.some(m => m.body === 'integration-test-message'));

aliceWs.close();
await delay(100);
const aliceWs2 = await connect(a.data.token);
assert.equal(aliceWs2.readyState, WebSocket.OPEN, 'reconnect must succeed with a valid session');

const outgoingToAlice = nextMessage(aliceWs2);
const reply = await request('/api/messages', { method: 'POST', headers: { Authorization: `Bearer ${b.data.token}` }, body: JSON.stringify({ recipient: alice, body: 'reconnect-test-message' }) });
assert.equal(reply.response.status, 201);
const aliceMessage = await outgoingToAlice;
assert.equal(aliceMessage.body, 'reconnect-test-message');
assert.equal(aliceMessage.sender, bob);

aliceWs2.close();
bobWs.close();
console.log(`Integration tests passed for ${alice} and ${bob}`);
