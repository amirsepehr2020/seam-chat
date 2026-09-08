import express from 'express';
import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '0.0.0.0';
const CORS_ORIGIN = process.env.CORS_ORIGIN || '*';
const SESSION_DAYS = Math.max(1, Math.min(90, Number(process.env.SESSION_DAYS || 30)));
const PASSWORD_PEPPER = process.env.PASSWORD_PEPPER || 'change-this-before-production';
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const DATA_FILE = path.join(DATA_DIR, 'seam-chat.json');
const USERNAME_RE = /^[A-Za-z0-9_]{3,24}$/;
const MAX_BODY = 4000;

fs.mkdirSync(DATA_DIR, { recursive: true });
function emptyDb(){return{users:[],sessions:[],messages:[],nextUserId:1,nextMessageId:1}}
function loadDb(){if(!fs.existsSync(DATA_FILE))return emptyDb();try{return{...emptyDb(),...JSON.parse(fs.readFileSync(DATA_FILE,'utf8'))}}catch{console.error('Could not read data file. Refusing to overwrite it.');process.exit(1)}}
let db=loadDb();let writeQueue=Promise.resolve();
function saveDb(){const snapshot=JSON.stringify(db,null,2);const tmp=`${DATA_FILE}.tmp`;writeQueue=writeQueue.then(async()=>{await fs.promises.writeFile(tmp,snapshot,'utf8');await fs.promises.rename(tmp,DATA_FILE)}).catch(error=>console.error('Database write failed:',error));return writeQueue}
function queueSave(){saveDb().catch(()=>{})}
function now(){return new Date().toISOString()}
function randomHex(bytes=32){return crypto.randomBytes(bytes).toString('hex')}
function sha256(value){return crypto.createHash('sha256').update(value).digest('hex')}
function hashPassword(password,salt){return crypto.pbkdf2Sync(`${password}:${PASSWORD_PEPPER}`,salt,210000,32,'sha256').toString('hex')}
function safeEqual(a,b){const aa=Buffer.from(String(a));const bb=Buffer.from(String(b));return aa.length===bb.length&&crypto.timingSafeEqual(aa,bb)}
function validUsername(value){return typeof value==='string'&&USERNAME_RE.test(value)}
function validPassword(value){return typeof value==='string'&&value.length>=8&&value.length<=128}
function validBody(value){return typeof value==='string'&&value.trim().length>0&&value.length<=MAX_BODY}
function publicUser(user){return{username:user.username}}
function serializeMessage(m){return{id:m.id,sender:db.users.find(u=>u.id===m.senderId)?.username,recipient:db.users.find(u=>u.id===m.recipientId)?.username,body:m.body,createdAt:m.createdAt,deliveredAt:m.deliveredAt||null,readAt:m.readAt||null}}

const app=express();app.disable('x-powered-by');app.set('trust proxy',1);app.use(express.json({limit:'32kb'}));
app.use((req,res,next)=>{res.setHeader('Access-Control-Allow-Origin',CORS_ORIGIN);res.setHeader('Access-Control-Allow-Headers','Content-Type, Authorization');res.setHeader('Access-Control-Allow-Methods','GET,POST,OPTIONS');res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('X-Frame-Options','DENY');res.setHeader('Referrer-Policy','no-referrer');if(req.method==='OPTIONS')return res.sendStatus(204);next()});
const sockets=new Map();const rateBuckets=new Map();
function rateLimit(key,limit,windowMs){const t=Date.now();const bucket=rateBuckets.get(key);if(!bucket||t-bucket.started>=windowMs){rateBuckets.set(key,{started:t,count:1});return true}if(bucket.count>=limit)return false;bucket.count++;return true}
function authToken(req){const header=req.get('authorization')||'';return/^Bearer\s+/i.test(header)?header.replace(/^Bearer\s+/i,'').trim():''}
function getUser(req){const token=authToken(req);if(!token)return null;const session=db.sessions.find(s=>s.tokenHash===sha256(token)&&Date.parse(s.expiresAt)>Date.now());return session?db.users.find(u=>u.id===session.userId)||null:null}
function requireUser(req,res){const user=getUser(req);if(!user){res.status(401).json({error:'Authentication required or session expired.'});return null}return user}
function createSession(userId){const token=randomHex(32);const expiresAt=new Date(Date.now()+SESSION_DAYS*86400000).toISOString();db.sessions.push({tokenHash:sha256(token),userId,expiresAt});return{token,expiresAt}}
function isOnline(userId){return sockets.has(userId)}
function sendToUser(userId,payload){const set=sockets.get(userId);if(!set)return;const text=JSON.stringify(payload);for(const ws of set)if(ws.readyState===ws.OPEN)ws.send(text)}
function broadcastPresence(){const users=db.users.filter(u=>isOnline(u.id)).map(u=>u.username);for(const set of sockets.values())for(const ws of set)if(ws.readyState===ws.OPEN)ws.send(JSON.stringify({type:'presence',users}))}
function touchDelivered(recipientId){const timestamp=now();let changed=false;for(const message of db.messages)if(message.recipientId===recipientId&&!message.deliveredAt){message.deliveredAt=timestamp;changed=true}return changed}

app.get('/health',(_req,res)=>res.json({ok:true,service:'seam-chat-node',timestamp:now()}));
app.post('/api/auth/register',async(req,res)=>{const ip=req.ip||req.socket.remoteAddress||'unknown';if(!rateLimit(`auth:${ip}`,30,15*60*1000))return res.status(429).json({error:'Too many authentication attempts.'});const{username,password}=req.body||{};if(!validUsername(username))return res.status(400).json({error:'Username must be 3–24 letters, numbers, or underscores.'});if(!validPassword(password))return res.status(400).json({error:'Password must be 8–128 characters.'});if(db.users.some(u=>u.username.toLowerCase()===username.toLowerCase()))return res.status(409).json({error:'Username is already taken.'});const salt=randomHex(16);const user={id:db.nextUserId++,username,passwordHash:hashPassword(password,salt),passwordSalt:salt,createdAt:now()};db.users.push(user);const{token,expiresAt}=createSession(user.id);await saveDb();res.status(201).json({token,expiresAt,user:publicUser(user)})});
app.post('/api/auth/login',async(req,res)=>{const ip=req.ip||req.socket.remoteAddress||'unknown';if(!rateLimit(`auth:${ip}`,30,15*60*1000))return res.status(429).json({error:'Too many authentication attempts.'});const{username,password}=req.body||{};if(!validUsername(username)||!validPassword(password))return res.status(401).json({error:'Invalid username or password.'});const user=db.users.find(u=>u.username.toLowerCase()===username.toLowerCase());if(!user||!safeEqual(hashPassword(password,user.passwordSalt),user.passwordHash))return res.status(401).json({error:'Invalid username or password.'});const{token,expiresAt}=createSession(user.id);await saveDb();res.json({token,expiresAt,user:publicUser(user)})});
app.post('/api/auth/logout',async(req,res)=>{const token=authToken(req);const user=requireUser(req,res);if(!user)return;db.sessions=db.sessions.filter(s=>s.tokenHash!==sha256(token));await saveDb();res.sendStatus(204)});
app.get('/api/me',(req,res)=>{const user=requireUser(req,res);if(user)res.json({user:publicUser(user)})});

app.get('/api/users/search',(req,res)=>{const user=requireUser(req,res);if(!user)return;const exact=String(req.query.username||'').trim();const q=String(req.query.q??exact).trim().toLowerCase();if(!q)return res.json(exact?{user:null,users:[]}:{users:[]});if(exact){const target=db.users.find(u=>u.username.toLowerCase()===exact.toLowerCase()&&u.id!==user.id);return res.json({user:target?publicUser(target):null,users:target?[publicUser(target)]:[]})}const users=db.users.filter(u=>u.id!==user.id&&u.username.toLowerCase().includes(q)).sort((a,b)=>{const as=a.username.toLowerCase(),bs=b.username.toLowerCase();const ap=as.startsWith(q)?0:1,bp=bs.startsWith(q)?0:1;return ap-bp||as.localeCompare(bs)}).slice(0,20).map(publicUser);res.json({users})});
app.get('/api/presence',(req,res)=>{const user=requireUser(req,res);if(!user)return;res.json({users:db.users.filter(u=>u.id!==user.id&&isOnline(u.id)).map(u=>u.username)})});
app.get('/api/chats',(req,res)=>{const user=requireUser(req,res);if(!user)return;const map=new Map();for(const m of db.messages){if(m.senderId!==user.id&&m.recipientId!==user.id)continue;const otherId=m.senderId===user.id?m.recipientId:m.senderId;const other=db.users.find(u=>u.id===otherId);if(!other)continue;const previous=map.get(otherId);if(!previous||Date.parse(m.createdAt)>Date.parse(previous.lastMessageAt))map.set(otherId,{username:other.username,lastMessage:m.body,lastMessageAt:m.createdAt,unreadCount:0})}for(const chat of map.values()){const other=db.users.find(u=>u.username===chat.username);chat.unreadCount=db.messages.filter(m=>m.senderId===other.id&&m.recipientId===user.id&&!m.readAt).length}res.json({chats:[...map.values()].sort((a,b)=>Date.parse(b.lastMessageAt)-Date.parse(a.lastMessageAt))})});
app.get('/api/messages/:username',(req,res)=>{const user=requireUser(req,res);if(!user)return;const other=db.users.find(u=>u.username.toLowerCase()===String(req.params.username).toLowerCase());if(!other)return res.status(404).json({error:'User not found.'});const limit=Math.min(Math.max(Number.parseInt(req.query.limit||'50',10)||50,1),100);const before=Number.parseInt(req.query.before||'',10);let messages=db.messages.filter(m=>(m.senderId===user.id&&m.recipientId===other.id)||(m.senderId===other.id&&m.recipientId===user.id));messages.sort((a,b)=>a.id-b.id);if(Number.isSafeInteger(before)&&before>0)messages=messages.filter(m=>m.id<before);const page=messages.slice(-limit).map(serializeMessage);res.json({messages:page,hasMore:messages.length>page.length})});
app.post('/api/messages/:username/read',(req,res)=>{const user=requireUser(req,res);if(!user)return;const other=db.users.find(u=>u.username.toLowerCase()===String(req.params.username).toLowerCase());if(!other)return res.status(404).json({error:'User not found.'});const timestamp=now(),ids=[];for(const m of db.messages)if(m.senderId===other.id&&m.recipientId===user.id&&!m.readAt){m.readAt=timestamp;m.deliveredAt ||= timestamp;ids.push(m.id)}if(ids.length){queueSave();sendToUser(other.id,{type:'read',from:user.username,ids,readAt:timestamp})}res.json({updated:ids.length})});
app.post('/api/messages',(req,res)=>{const user=requireUser(req,res);if(!user)return;const ip=req.ip||req.socket.remoteAddress||'unknown';if(!rateLimit(`msg:${user.id}:${ip}`,120,60*1000))return res.status(429).json({error:'Message rate limit exceeded.'});const{recipient,body,clientMessageId=null}=req.body||{};if(!validUsername(recipient))return res.status(400).json({error:'Recipient username is invalid.'});if(!validBody(body))return res.status(400).json({error:'Message must contain 1–4000 characters.'});const target=db.users.find(u=>u.username.toLowerCase()===recipient.toLowerCase());if(!target)return res.status(404).json({error:'Recipient not found.'});if(target.id===user.id)return res.status(400).json({error:'You cannot message yourself.'});if(clientMessageId){const duplicate=db.messages.find(m=>m.senderId===user.id&&m.clientMessageId===clientMessageId);if(duplicate)return res.json({message:serializeMessage(duplicate)})}const deliveredAt=isOnline(target.id)?now():null;const message={id:db.nextMessageId++,senderId:user.id,recipientId:target.id,body:body.trim(),createdAt:now(),deliveredAt,readAt:null,clientMessageId:clientMessageId||null};db.messages.push(message);const payload={type:'message',...serializeMessage(message)};sendToUser(target.id,payload);sendToUser(user.id,payload);res.status(201).json({message:payload});queueSave()});

const server=http.createServer(app);const wss=new WebSocketServer({noServer:true});
server.on('upgrade',(request,socket,head)=>{const url=new URL(request.url,`http://${request.headers.host}`);if(url.pathname!=='/ws')return socket.destroy();const token=url.searchParams.get('token')||'';const session=db.sessions.find(s=>s.tokenHash===sha256(token)&&Date.parse(s.expiresAt)>Date.now());const user=session?db.users.find(u=>u.id===session.userId):null;if(!user)return socket.destroy();wss.handleUpgrade(request,socket,head,ws=>wss.emit('connection',ws,user))});
wss.on('connection',(ws,user)=>{if(!sockets.has(user.id))sockets.set(user.id,new Set());sockets.get(user.id).add(ws);if(touchDelivered(user.id))queueSave();broadcastPresence();ws.send(JSON.stringify({type:'presence',users:db.users.filter(u=>u.id!==user.id&&isOnline(u.id)).map(u=>u.username)}));ws.on('message',raw=>{let payload;try{payload=JSON.parse(raw.toString())}catch{return}if(payload?.type==='typing'&&typeof payload.recipient==='string'){const target=db.users.find(u=>u.username.toLowerCase()===payload.recipient.toLowerCase());if(target)sendToUser(target.id,{type:'typing',from:user.username,active:Boolean(payload.active)})}if(payload?.type==='ping')ws.send(JSON.stringify({type:'pong'}))});ws.on('close',()=>{const set=sockets.get(user.id);if(!set)return;set.delete(ws);if(!set.size)sockets.delete(user.id);broadcastPresence()})});
setInterval(()=>{const cutoff=Date.now()-60*60*1000;for(const[key,bucket]of rateBuckets)if(bucket.started<cutoff)rateBuckets.delete(key)},10*60*1000).unref();
server.listen(PORT,HOST,()=>{console.log(`SEAM CHAT backend listening on http://${HOST}:${PORT}`);console.log(`Data file: ${DATA_FILE}`);if(PASSWORD_PEPPER==='change-this-before-production')console.warn('WARNING: Set PASSWORD_PEPPER before real use.')});
