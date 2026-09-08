import express from 'express';
import http from 'node:http';
import crypto from 'node:crypto';
import pg from 'pg';
import { WebSocketServer } from 'ws';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';

const { Pool } = pg;
const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
  max: Number(process.env.DB_POOL_MAX || 10),
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000
});
const PORT = Number(process.env.PORT || 3000);
const SESSION_DAYS = 30;
const sockets = new Map();
const typingTimers = new Map();

app.disable('x-powered-by');
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(express.json({ limit: '64kb' }));
app.use('/api/auth', rateLimit({ windowMs: 15 * 60 * 1000, limit: 30, standardHeaders: 'draft-8', legacyHeaders: false }));
app.use('/api/messages', rateLimit({ windowMs: 60 * 1000, limit: 120, standardHeaders: 'draft-8', legacyHeaders: false }));

const allowedOrigins = (process.env.CORS_ORIGIN || '*').split(',').map(v => v.trim()).filter(Boolean);
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (allowedOrigins.includes('*')) res.setHeader('Access-Control-Allow-Origin', '*');
  else if (origin && allowedOrigins.includes(origin)) { res.setHeader('Access-Control-Allow-Origin', origin); res.setHeader('Vary', 'Origin'); }
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

async function initDb() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id BIGSERIAL PRIMARY KEY,
      username VARCHAR(24) NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      salt TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE TABLE IF NOT EXISTS sessions (
      token_hash TEXT PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      expires_at TIMESTAMPTZ NOT NULL
    );
    CREATE TABLE IF NOT EXISTS messages (
      id BIGSERIAL PRIMARY KEY,
      sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      body VARCHAR(4000) NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      delivered_at TIMESTAMPTZ,
      read_at TIMESTAMPTZ
    );
    CREATE INDEX IF NOT EXISTS messages_pair_idx ON messages(sender_id, recipient_id, id DESC);
    CREATE INDEX IF NOT EXISTS messages_recipient_idx ON messages(recipient_id, id DESC);
    CREATE INDEX IF NOT EXISTS messages_unread_idx ON messages(recipient_id, read_at, id DESC);
    CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);
  `);
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS salt TEXT');
  await pool.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ');
  await pool.query('ALTER TABLE messages ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ');
  await pool.query("UPDATE users SET salt = encode(gen_random_bytes(16), 'hex') WHERE salt IS NULL").catch(() => {});
  await pool.query('DELETE FROM sessions WHERE expires_at <= NOW()');
}
function validUsername(username) { return typeof username === 'string' && /^[a-zA-Z0-9_]{3,24}$/.test(username); }
function validPassword(password) { return typeof password === 'string' && password.length >= 8 && password.length <= 128; }
function validBody(body) { return typeof body === 'string' && body.trim().length > 0 && body.length <= 4000; }
function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) { return { salt, hash: crypto.scryptSync(password, salt, 64).toString('hex') }; }
function verifyPassword(password, salt, expected) { if (!salt || !expected) return false; const actual = crypto.scryptSync(password, salt, 64).toString('hex'); const a = Buffer.from(actual, 'hex'); const b = Buffer.from(expected, 'hex'); return a.length === b.length && crypto.timingSafeEqual(a, b); }
function tokenHash(token) { return crypto.createHash('sha256').update(token).digest('hex'); }
async function createSession(userId) { const token = crypto.randomBytes(32).toString('hex'); await pool.query('INSERT INTO sessions(token_hash,user_id,expires_at) VALUES($1,$2,NOW()+$3::interval)', [tokenHash(token), userId, `${SESSION_DAYS} days`]); return token; }
async function getUserByToken(token) { if (!token) return null; const { rows } = await pool.query('SELECT u.id,u.username FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=$1 AND s.expires_at>NOW()', [tokenHash(token)]); return rows[0] || null; }
async function auth(req,res,next) { try { const token=req.headers.authorization?.replace(/^Bearer\s+/i,''); const user=await getUserByToken(token); if(!user)return res.status(401).json({error:'Authentication required or session expired.'}); req.user=user; req.sessionToken=token; next(); } catch { res.status(500).json({error:'Authentication service unavailable.'}); } }
function broadcast(userId,payload) { const set=sockets.get(String(userId)); if(!set)return; const data=JSON.stringify(payload); for(const ws of set)if(ws.readyState===1)ws.send(data); }
function isOnline(userId) { return Boolean(sockets.get(String(userId))?.size); }
function broadcastPresence(username,online,exceptUserId=null) { for(const [userId,set] of sockets){ if(String(userId)===String(exceptUserId))continue; const data=JSON.stringify({type:'presence',username,online}); for(const ws of set)if(ws.readyState===1)ws.send(data); } }

app.get('/health',async(_req,res)=>{try{await pool.query('SELECT 1');res.json({ok:true,service:'seam-chat-backend',timestamp:new Date().toISOString()});}catch{res.status(503).json({ok:false});}});
app.post('/api/auth/register',async(req,res)=>{const {username,password}=req.body||{};if(!validUsername(username))return res.status(400).json({error:'Username must be 3–24 letters, numbers, or underscores.'});if(!validPassword(password))return res.status(400).json({error:'Password must be 8–128 characters.'});const {salt,hash}=hashPassword(password);try{const {rows}=await pool.query('INSERT INTO users(username,password_hash,salt) VALUES($1,$2,$3) RETURNING id,username',[username,hash,salt]);const token=await createSession(rows[0].id);res.status(201).json({token,user:{username:rows[0].username}});}catch(error){if(error.code==='23505')return res.status(409).json({error:'Username is already taken.'});res.status(500).json({error:'Registration failed.'});}});
app.post('/api/auth/login',async(req,res)=>{const {username,password}=req.body||{};if(!validUsername(username)||!validPassword(password))return res.status(401).json({error:'Invalid username or password.'});try{const {rows}=await pool.query('SELECT id,username,password_hash,salt FROM users WHERE username=$1',[username]);const user=rows[0];if(!user||!verifyPassword(password,user.salt,user.password_hash))return res.status(401).json({error:'Invalid username or password.'});const token=await createSession(user.id);res.json({token,user:{username:user.username}});}catch{res.status(500).json({error:'Login failed.'});}});
app.post('/api/auth/logout',auth,async(req,res)=>{await pool.query('DELETE FROM sessions WHERE token_hash=$1',[tokenHash(req.sessionToken)]);res.status(204).end();});
app.get('/api/me',auth,(req,res)=>res.json({user:{username:req.user.username}}));
app.get('/api/users/search',auth,async(req,res)=>{const username=String(req.query.username||'').trim();if(!validUsername(username))return res.json({user:null});const {rows}=await pool.query('SELECT username FROM users WHERE username=$1 AND id<>$2',[username,req.user.id]);res.json({user:rows[0]||null});});
app.get('/api/presence',auth,async(_req,res)=>{const usernames=[];for(const set of sockets.values()){if(!set.size)continue;}const {rows}=await pool.query('SELECT id,username FROM users');for(const user of rows)if(isOnline(user.id))usernames.push(user.username);res.json({users:usernames});});
app.get('/api/chats',auth,async(req,res)=>{const {rows}=await pool.query(`SELECT u.username,m.body AS "lastMessage",m.created_at AS "lastMessageAt",COALESCE(unread.count,0)::int AS "unreadCount" FROM users u JOIN LATERAL (SELECT body,created_at FROM messages WHERE (sender_id=$1 AND recipient_id=u.id) OR (sender_id=u.id AND recipient_id=$1) ORDER BY id DESC LIMIT 1) m ON TRUE LEFT JOIN LATERAL (SELECT COUNT(*)::int AS count FROM messages WHERE sender_id=u.id AND recipient_id=$1 AND read_at IS NULL) unread ON TRUE WHERE u.id<>$1 ORDER BY m.created_at DESC`,[req.user.id]);res.json({chats:rows});});
app.get('/api/messages/:username',auth,async(req,res)=>{const limit=Math.min(Math.max(Number.parseInt(req.query.limit,10)||50,1),100);const before=Number.parseInt(req.query.before,10);const {rows:users}=await pool.query('SELECT id,username FROM users WHERE username=$1',[req.params.username]);const other=users[0];if(!other)return res.status(404).json({error:'User not found.'});const params=[req.user.id,other.id,limit];const beforeClause=Number.isSafeInteger(before)&&before>0?' AND m.id < $4':'';if(beforeClause)params.push(before);const {rows}=await pool.query(`SELECT m.id,s.username AS sender,r.username AS recipient,m.body,m.created_at AS "createdAt",m.delivered_at AS "deliveredAt",m.read_at AS "readAt" FROM messages m JOIN users s ON s.id=m.sender_id JOIN users r ON r.id=m.recipient_id WHERE ((m.sender_id=$1 AND m.recipient_id=$2) OR (m.sender_id=$2 AND m.recipient_id=$1))${beforeClause} ORDER BY m.id DESC LIMIT $3`,params);res.json({messages:rows.reverse(),hasMore:rows.length===limit});});
app.post('/api/messages',auth,async(req,res)=>{const {recipient,body}=req.body||{};if(!validUsername(recipient))return res.status(400).json({error:'Recipient username is invalid.'});if(!validBody(body))return res.status(400).json({error:'Message must contain 1–4000 characters.'});const {rows:targets}=await pool.query('SELECT id,username FROM users WHERE username=$1',[recipient]);const target=targets[0];if(!target)return res.status(404).json({error:'Recipient not found.'});if(String(target.id)===String(req.user.id))return res.status(400).json({error:'You cannot message yourself.'});const clean=body.trim();const delivered=isOnline(target.id);const {rows}=await pool.query('INSERT INTO messages(sender_id,recipient_id,body,delivered_at) VALUES($1,$2,$3,CASE WHEN $4 THEN NOW() ELSE NULL END) RETURNING id,body,created_at AS "createdAt",delivered_at AS "deliveredAt",read_at AS "readAt"',[req.user.id,target.id,clean,delivered]);const message={type:'message',id:rows[0].id,sender:req.user.username,recipient:target.username,body:rows[0].body,createdAt:rows[0].createdAt,deliveredAt:rows[0].deliveredAt,readAt:rows[0].readAt};broadcast(target.id,message);broadcast(req.user.id,message);res.status(201).json({message});});
app.post('/api/messages/:username/read',auth,async(req,res)=>{const {rows:users}=await pool.query('SELECT id,username FROM users WHERE username=$1',[req.params.username]);const other=users[0];if(!other)return res.status(404).json({error:'User not found.'});const {rows}=await pool.query('UPDATE messages SET read_at=COALESCE(read_at,NOW()),delivered_at=COALESCE(delivered_at,NOW()) WHERE sender_id=$1 AND recipient_id=$2 AND read_at IS NULL RETURNING id,read_at AS "readAt"',[other.id,req.user.id]);if(rows.length)broadcast(other.id,{type:'read',from:req.user.username,ids:rows.map(r=>r.id),readAt:rows[0].readAt});res.json({updated:rows.length});});

server.on('upgrade',async(req,socket,head)=>{if(!req.url?.startsWith('/ws'))return socket.destroy();try{const token=new URL(req.url,'http://localhost').searchParams.get('token');const user=await getUserByToken(token);if(!user)return socket.destroy();wss.handleUpgrade(req,socket,head,ws=>{ws.user=user;wss.emit('connection',ws);});}catch{socket.destroy();}});
wss.on('connection',ws=>{const key=String(ws.user.id);if(!sockets.has(key))sockets.set(key,new Set());const firstConnection=!sockets.get(key).size;sockets.get(key).add(ws);if(firstConnection)broadcastPresence(ws.user.username,true,ws.user.id);for(const [id,set] of sockets)if(set.size&&String(id)!==key){pool.query('SELECT username FROM users WHERE id=$1',[id]).then(({rows})=>{if(rows[0])broadcast(ws.user.id,{type:'presence',username:rows[0].username,online:true});}).catch(()=>{});}ws.on('message',raw=>{try{const data=JSON.parse(raw.toString());if(data.type!=='typing'||typeof data.username!=='string')return;const targetUsername=data.username;if(targetUsername===ws.user.username)return;pool.query('SELECT id FROM users WHERE username=$1',[targetUsername]).then(({rows})=>{const target=rows[0];if(!target||!isOnline(target.id))return;const keyTimer=`${ws.user.id}:${target.id}`;clearTimeout(typingTimers.get(keyTimer));broadcast(target.id,{type:'typing',username:ws.user.username,active:Boolean(data.active)});if(data.active)typingTimers.set(keyTimer,setTimeout(()=>broadcast(target.id,{type:'typing',username:ws.user.username,active:false}),2500));}).catch(()=>{});}catch{}});ws.on('close',()=>{const set=sockets.get(key);set?.delete(ws);if(!set?.size){sockets.delete(key);broadcastPresence(ws.user.username,false,ws.user.id);}});});

const cleanup=setInterval(()=>pool.query('DELETE FROM sessions WHERE expires_at<=NOW()').catch(()=>{}),60*60*1000);cleanup.unref();

initDb().then(()=>server.listen(PORT,()=>console.log(`SEAM CHAT backend listening on ${PORT}`))).catch(error=>{console.error(error);process.exit(1);});
