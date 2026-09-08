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
function nextMessage(ws, predicate = () => true, timeout = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket message timeout')), timeout);
    const handler = data => {
      let parsed;
      try { parsed = JSON.parse(data.toString()); } catch { return; }
      if (!predicate(parsed)) return;
      clearTimeout(timer); ws.off('message', handler); resolve(parsed);
    };
    ws.on('message', handler);
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

const incomingAtBob = nextMessage(bobWs, m => m.type === 'message');
const sent = await request('/api/messages', { method: 'POST', headers: { Authorization: `Bearer ${a.data.token}` }, body: JSON.stringify({ recipient: bob, body: 'integration-test-message' }) });
assert.equal(sent.response.status, 201);
assert.ok(sent.data.message.deliveredAt, 'online recipient should get delivered timestamp');
const bobMessage = await incomingAtBob;
assert.equal(bobMessage.type, 'message');
assert.equal(bobMessage.sender, alice);
assert.equal(bobMessage.recipient, bob);
assert.equal(bobMessage.body, 'integration-test-message');
assert.ok(bobMessage.deliveredAt);

const readEvent = nextMessage(aliceWs, m => m.type === 'read');
const read = await request(`/api/messages/${encodeURIComponent(alice)}/read`, { method: 'POST', headers: { Authorization: `Bearer ${b.data.token}` } });
assert.equal(read.response.status, 200);
assert.equal(read.data.updated, 1);
const readNotice = await readEvent;
assert.equal(readNotice.type, 'read');
assert.ok(readNotice.ids.length >= 1);

const history = await request(`/api/messages/${encodeURIComponent(alice)}?limit=1`, { headers: { Authorization: `Bearer ${b.data.token}` } });
assert.equal(history.response.status, 200);
assert.equal(history.data.messages.length, 1);
assert.equal(history.data.messages[0].body, 'integration-test-message');

const typingEvent = nextMessage(bobWs, m => m.type === 'typing');
aliceWs.send(JSON.stringify({ type: 'typing', username: bob, active: true }));
const typing = await typingEvent;
assert.equal(typing.active, true);
assert.equal(typing.username, alice);

aliceWs.close();
await delay(100);
const aliceWs2 = await connect(a.data.token);
assert.equal(aliceWs2.readyState, WebSocket.OPEN, 'reconnect must succeed with a valid session');

const outgoingToAlice = nextMessage(aliceWs2, m => m.type === 'message');
const reply = await request('/api/messages', { method: 'POST', headers: { Authorization: `Bearer ${b.data.token}` }, body: JSON.stringify({ recipient: alice, body: 'reconnect-test-message' }) });
assert.equal(reply.response.status, 201);
const aliceMessage = await outgoingToAlice;
assert.equal(aliceMessage.body, 'reconnect-test-message');
assert.equal(aliceMessage.sender, bob);

aliceWs2.close();
bobWs.close();
console.log(`Integration tests passed for ${alice} and ${bob}`);
