import express from 'express';
import http from 'node:http';
import crypto from 'node:crypto';
import Database from 'better-sqlite3';
import { WebSocketServer } from 'ws';

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });
const db = new Database(process.env.SEAM_DB || 'seam-chat.db');
db.pragma('journal_mode = WAL');

db.exec(`
CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password_hash TEXT NOT NULL, salt TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, user_id INTEGER NOT NULL, expires_at INTEGER NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id));
CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL, body TEXT NOT NULL, created_at TEXT NOT NULL, FOREIGN KEY(sender_id) REFERENCES users(id), FOREIGN KEY(recipient_id) REFERENCES users(id));
CREATE INDEX IF NOT EXISTS messages_pair ON messages(sender_id, recipient_id, id);
`);

app.use(express.json({ limit: '64kb' }));
app.use(express.static('.'));

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  return { salt, hash: crypto.scryptSync(password, salt, 64).toString('hex') };
}
function verifyPassword(password, salt, expected) {
  const actual = crypto.scryptSync(password, salt, 64).toString('hex');
  return crypto.timingSafeEqual(Buffer.from(actual, 'hex'), Buffer.from(expected, 'hex'));
}
function createSession(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  const expires = Date.now() + 1000 * 60 * 60 * 24 * 30;
  db.prepare('INSERT INTO sessions(token,user_id,expires_at) VALUES(?,?,?)').run(token, userId, expires);
  return token;
}
function auth(req, res, next) {
  const token = req.headers.authorization?.replace(/^Bearer\s+/i, '');
  if (!token) return res.status(401).json({ error: 'Authentication required' });
  const row = db.prepare('SELECT u.id,u.username,s.token FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=? AND s.expires_at>?').get(token, Date.now());
  if (!row) return res.status(401).json({ error: 'Session expired' });
  req.user = { id: row.id, username: row.username, token: row.token }; next();
}
function validUsername(username) { return /^[a-zA-Z0-9_]{3,24}$/.test(username); }
function validBody(body) { return typeof body === 'string' && body.trim().length > 0 && body.length <= 4000; }

app.post('/api/auth/register', (req, res) => {
  const { username, password } = req.body || {};
  if (!validUsername(username)) return res.status(400).json({ error: 'Username must be 3–24 letters, numbers, or underscores.' });
  if (typeof password !== 'string' || password.length < 8) return res.status(400).json({ error: 'Password must be at least 8 characters.' });
  const { salt, hash } = hashPassword(password);
  try {
    const result = db.prepare('INSERT INTO users(username,password_hash,salt,created_at) VALUES(?,?,?,?)').run(username, hash, salt, new Date().toISOString());
    res.json({ token: createSession(result.lastInsertRowid), user: { username } });
  } catch { res.status(409).json({ error: 'Username is already taken.' }); }
});

app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body || {};
  const user = db.prepare('SELECT * FROM users WHERE username=?').get(username || '');
  if (!user || !verifyPassword(password || '', user.salt, user.password_hash)) return res.status(401).json({ error: 'Invalid username or password.' });
  res.json({ token: createSession(user.id), user: { username: user.username } });
});

app.get('/api/me', auth, (req, res) => res.json({ user: { username: req.user.username } }));
app.get('/api/users/search', auth, (req, res) => {
  const user = db.prepare('SELECT username FROM users WHERE username=?').get(req.query.username || '');
  res.json({ user: user || null });
});

app.get('/api/chats', auth, (req, res) => {
  const chats = db.prepare(`SELECT u.username, m.body AS lastMessage, m.created_at AS lastMessageAt FROM users u JOIN (SELECT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS other_id, MAX(id) AS last_id FROM messages WHERE sender_id=? OR recipient_id=? GROUP BY other_id) x ON x.other_id=u.id JOIN messages m ON m.id=x.last_id ORDER BY m.id DESC`).all(req.user.id, req.user.id, req.user.id);
  res.json({ chats });
});

app.get('/api/messages/:username', auth, (req, res) => {
  const other = db.prepare('SELECT id,username FROM users WHERE username=?').get(req.params.username);
  if (!other) return res.status(404).json({ error: 'User not found' });
  const messages = db.prepare(`SELECT m.id, su.username AS sender, ru.username AS recipient, m.body, m.created_at AS createdAt FROM messages m JOIN users su ON su.id=m.sender_id JOIN users ru ON ru.id=m.recipient_id WHERE (m.sender_id=? AND m.recipient_id=?) OR (m.sender_id=? AND m.recipient_id=?) ORDER BY m.id ASC`).all(req.user.id, other.id, other.id, req.user.id);
  res.json({ messages });
});

app.post('/api/messages', auth, (req, res) => {
  const { recipient, body } = req.body || {};
  if (!validBody(body)) return res.status(400).json({ error: 'Message must contain 1–4000 characters.' });
  const target = db.prepare('SELECT id,username FROM users WHERE username=?').get(recipient || '');
  if (!target) return res.status(404).json({ error: 'Recipient not found.' });
  const createdAt = new Date().toISOString();
  const result = db.prepare('INSERT INTO messages(sender_id,recipient_id,body,created_at) VALUES(?,?,?,?)').run(req.user.id, target.id, body.trim(), createdAt);
  const message = { id: result.lastInsertRowid, sender: req.user.username, recipient: target.username, body: body.trim(), createdAt };
  broadcast(target.id, message); broadcast(req.user.id, message);
  res.status(201).json({ message });
});

const sockets = new Map();
function broadcast(userId, payload) { const set = sockets.get(userId); if (!set) return; for (const ws of set) if (ws.readyState === 1) ws.send(JSON.stringify({ type: 'message', ...payload })); }
function userFromToken(token) { return db.prepare('SELECT u.id,u.username FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=? AND s.expires_at>?').get(token, Date.now()); }
server.on('upgrade', (req, socket, head) => {
  if (!req.url?.startsWith('/ws')) return socket.destroy();
  const token = new URL(req.url, 'http://localhost').searchParams.get('token');
  const user = userFromToken(token || '');
  if (!user) return socket.destroy();
  wss.handleUpgrade(req, socket, head, ws => { ws.user = user; wss.emit('connection', ws); });
});
wss.on('connection', ws => { if (!sockets.has(ws.user.id)) sockets.set(ws.user.id, new Set()); sockets.get(ws.user.id).add(ws); ws.on('close', () => { sockets.get(ws.user.id)?.delete(ws); if (!sockets.get(ws.user.id)?.size) sockets.delete(ws.user.id); }); });

const port = Number(process.env.PORT || 3000);
server.listen(port, () => console.log(`SEAM CHAT listening on http://localhost:${port}`));
