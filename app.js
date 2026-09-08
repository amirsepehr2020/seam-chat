const $ = (s) => document.querySelector(s);
const state = { user: null, activeUser: null, socket: null, reconnectTimer: null, reconnectAttempt: 0, intentionalClose: false, chats: [], messages: new Map(), onlineUsers: new Set(), typingUsers: new Set(), loadingHistory: new Set() };
const tokenKey = 'seam_chat_token';
const apiBase = (window.SEAM_API_BASE || location.origin).replace(/\/$/, '');

function wsUrl() { const url = new URL(apiBase + '/ws'); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'; url.searchParams.set('token', localStorage.getItem(tokenKey) || ''); return url.toString(); }
function toast(text) { const el = $('#toast'); el.textContent = text; el.classList.add('show'); clearTimeout(window.__toast); window.__toast = setTimeout(() => el.classList.remove('show'), 2600); }
function initials(name) { return (name || '?').slice(0, 1).toUpperCase(); }
function authHeaders() { const token = localStorage.getItem(tokenKey); return token ? { Authorization: `Bearer ${token}` } : {}; }
async function api(path, options = {}) { const response = await fetch(`${apiBase}${path}`, { ...options, headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...authHeaders(), ...(options.headers || {}) } }); const data = await response.json().catch(() => ({})); if (!response.ok) { const error = new Error(data.error || `Request failed (${response.status})`); error.status = response.status; throw error; } return data; }
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;' }[c])); }
function messageStatus(m) { if (m.sender !== state.user.username) return ''; if (m.readAt) return ' · Read'; if (m.deliveredAt) return ' · Delivered'; return ' · Sent'; }

function renderChats(filter = '') {
  const q = filter.trim().toLowerCase(); const chats = state.chats.filter(c => c.username.toLowerCase().includes(q)); const list = $('#chatList');
  list.innerHTML = chats.length ? chats.map(c => `<button class="chat-row ${state.activeUser === c.username ? 'selected' : ''}" data-name="${escapeHtml(c.username)}"><span class="avatar avatar-purple">${escapeHtml(initials(c.username))}</span><span class="chat-copy"><strong>${escapeHtml(c.username)}</strong><small>${escapeHtml(c.lastMessage || 'No messages yet')}</small></span><span class="chat-meta"><time>${c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) : ''}</time>${c.unreadCount ? `<b>${c.unreadCount}</b>` : ''}</span></button>`).join('') : '<div class="empty-state">No conversations yet.</div>';
  list.querySelectorAll('.chat-row').forEach(row => row.addEventListener('click', () => openChat(row.dataset.name)));
}

function renderMessages() {
  const area = $('#messageArea'); const messages = state.messages.get(state.activeUser) || [];
  if (!messages.length) { area.innerHTML = '<div class="empty-conversation">No messages yet. Say hello.</div>'; return; }
  area.innerHTML = messages.map(m => `<article class="message ${m.sender === state.user.username ? 'sent' : 'received'}"><div>${m.sender !== state.user.username ? `<span class="bubble-avatar">${escapeHtml(initials(m.sender))}</span>` : ''}<p>${escapeHtml(m.body)}</p><time>${new Date(m.createdAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}${escapeHtml(messageStatus(m))}</time></div></article>`).join('');
  area.scrollTop = area.scrollHeight;
}

function addMessage(message) {
  const other = message.sender === state.user.username ? message.recipient : message.sender; const list = state.messages.get(other) || [];
  const existingIndex = list.findIndex(item => String(item.id) === String(message.id));
  if (existingIndex === -1) list.push(message); else list[existingIndex] = { ...list[existingIndex], ...message };
  list.sort((a,b) => Number(a.id) - Number(b.id)); state.messages.set(other, list);
  const existing = state.chats.find(c => c.username === other); if (existing) { existing.lastMessage = message.body; existing.lastMessageAt = message.createdAt; if (message.sender !== state.user.username && state.activeUser !== other) existing.unreadCount = (existing.unreadCount || 0) + 1; } else state.chats.unshift({ username: other, lastMessage: message.body, lastMessageAt: message.createdAt, unreadCount: message.sender === state.user.username ? 0 : 1 });
  state.chats.sort((a,b) => new Date(b.lastMessageAt || 0) - new Date(a.lastMessageAt || 0)); renderChats($('#searchInput').value); if (state.activeUser === other) { renderMessages(); if (message.sender !== state.user.username) markRead(other).catch(() => {}); }
}
function updateMessageStatus(messageId, patch) { for (const [username,list] of state.messages) { const message=list.find(m=>String(m.id)===String(messageId)); if(message){Object.assign(message,patch);if(username===state.activeUser)renderMessages();break;} } }
async function markRead(username) { const data=await api(`/api/messages/${encodeURIComponent(username)}/read`,{method:'POST'}); const chat=state.chats.find(c=>c.username===username); if(chat)chat.unreadCount=0; renderChats($('#searchInput').value); return data; }
async function loadMe() { const data = await api('/api/me'); state.user = data.user; $('#profileBtn').textContent = initials(state.user.username); }
async function loadChats() { const data = await api('/api/chats'); state.chats = data.chats; renderChats($('#searchInput').value); }
async function loadPresence() { try { const data=await api('/api/presence'); state.onlineUsers=new Set(data.users); updateActiveStatus(); } catch {} }
async function openChat(username) {
  if (!username || username === state.user.username) return toast('You cannot start a chat with yourself.'); state.activeUser = username;
  $('#activeName').textContent = username; $('#detailsName').textContent = username; $('#detailsUsername').textContent = `@${username}`; $('#activeAvatar').textContent = initials(username); $('#detailsAvatar').textContent = initials(username); updateActiveStatus(); updateTyping(); $('#messageInput').disabled = false; $('.send-btn').disabled = false; renderChats($('#searchInput').value);
  try { const data = await api(`/api/messages/${encodeURIComponent(username)}?limit=50`); state.messages.set(username, data.messages); renderMessages(); await markRead(username); } catch (error) { toast(error.status === 404 ? 'User not found.' : error.message); }
}
async function loadOlderMessages() { const username=state.activeUser; if(!username||state.loadingHistory.has(username))return; const list=state.messages.get(username)||[]; if(!list.length)return; state.loadingHistory.add(username); try { const data=await api(`/api/messages/${encodeURIComponent(username)}?limit=50&before=${encodeURIComponent(list[0].id)}`); state.messages.set(username,[...data.messages,...list]); const area=$('#messageArea'); const oldHeight=area.scrollHeight; renderMessages(); area.scrollTop=area.scrollHeight-oldHeight; if(!data.hasMore)$('#loadOlder').hidden=true; } catch(error){toast(error.message);} finally{state.loadingHistory.delete(username);} }
function updateActiveStatus() { if (state.activeUser) $('#activeStatus').textContent = state.typingUsers.has(state.activeUser) ? 'typing…' : (state.onlineUsers.has(state.activeUser) ? 'Online' : 'Offline'); }
function updateTyping() { updateActiveStatus(); }
function setConnectionStatus(text) { $('#connectionStatus').textContent = text; }
function scheduleReconnect() { if (state.intentionalClose || !localStorage.getItem(tokenKey) || state.reconnectTimer) return; const delay = Math.min(15000, 1000 * 2 ** state.reconnectAttempt); state.reconnectAttempt += 1; setConnectionStatus(`Reconnecting in ${Math.ceil(delay/1000)}s…`); state.reconnectTimer = setTimeout(() => { state.reconnectTimer = null; connectSocket(); }, delay); }
function connectSocket() {
  const token = localStorage.getItem(tokenKey); if (!token) return; if (state.socket && (state.socket.readyState === WebSocket.OPEN || state.socket.readyState === WebSocket.CONNECTING)) return; state.intentionalClose = false; setConnectionStatus('Connecting…');
  let socket; try { socket = new WebSocket(wsUrl()); } catch { scheduleReconnect(); return; } state.socket = socket;
  socket.onopen = () => { state.reconnectAttempt = 0; setConnectionStatus('Real-time connection active'); loadPresence(); };
  socket.onmessage = event => { try { const message = JSON.parse(event.data); if (message.type === 'message') addMessage(message); if (message.type === 'presence') { if (message.online) state.onlineUsers.add(message.username); else state.onlineUsers.delete(message.username); updateActiveStatus(); } if(message.type==='typing'){ if(message.active)state.typingUsers.add(message.username);else state.typingUsers.delete(message.username);updateActiveStatus(); } if(message.type==='read')for(const id of message.ids||[])updateMessageStatus(id,{readAt:message.readAt,deliveredAt:message.readAt}); } catch {} };
  socket.onerror = () => setConnectionStatus('Connection error');
  socket.onclose = () => { if (state.socket === socket) state.socket = null; if (!state.intentionalClose) scheduleReconnect(); else setConnectionStatus('Disconnected'); };
}
function sendTyping(active) { if(!state.socket||state.socket.readyState!==WebSocket.OPEN||!state.activeUser)return; state.socket.send(JSON.stringify({type:'typing',username:state.activeUser,active})); }
function logout() {
  state.intentionalClose = true; clearTimeout(state.reconnectTimer); state.reconnectTimer = null; if (state.socket) state.socket.close(1000, 'logout'); state.socket = null; localStorage.removeItem(tokenKey); state.user = null; state.activeUser = null; state.chats = []; state.messages.clear(); state.onlineUsers.clear(); state.typingUsers.clear(); $('#profileBtn').textContent = '?'; $('#activeName').textContent = 'Select a chat'; $('#activeStatus').textContent = '—'; $('#detailsName').textContent = 'No chat selected'; $('#detailsUsername').textContent = '—'; $('#messageInput').disabled = true; $('.send-btn').disabled = true; $('#messageArea').innerHTML = '<div class="empty-conversation">Sign in to start messaging.</div>'; renderChats(); $('#authDialog').showModal();
}

$('#composer').addEventListener('submit', async e => { e.preventDefault(); const input = $('#messageInput'); const body = input.value.trim(); if (!body || !state.activeUser) return; const button = $('.send-btn'); button.disabled = true; try { const data = await api('/api/messages', { method:'POST', body:JSON.stringify({ recipient:state.activeUser, body }) }); addMessage(data.message); input.value = ''; sendTyping(false); } catch (error) { if (error.status === 401) logout(); else toast(error.message); } finally { if (state.activeUser) button.disabled = false; input.focus(); } });
let typingStopTimer; $('#messageInput').addEventListener('input', () => { sendTyping(true); clearTimeout(typingStopTimer); typingStopTimer=setTimeout(()=>sendTyping(false),1200); });
$('#searchInput').addEventListener('input', e => renderChats(e.target.value));
$('#newChat').addEventListener('click', () => $('#newChatDialog').showModal());
$('#contactsBtn').addEventListener('click', () => $('#newChatDialog').showModal());
$('#newChatForm').addEventListener('submit', async e => { e.preventDefault(); const username = $('#targetUsername').value.trim(); try { const data = await api(`/api/users/search?username=${encodeURIComponent(username)}`); if (!data.user) throw new Error('User not found.'); $('#newChatDialog').close(); $('#targetUsername').value = ''; await openChat(data.user.username); } catch (error) { toast(error.message); } });
$('#authForm').addEventListener('submit', async e => { e.preventDefault(); const username = $('#authUsername').value.trim(); const password = $('#authPassword').value; const mode = $('#authMode').value; $('#authError').textContent = ''; $('#authSubmit').disabled = true; try { const endpoint = mode === 'register' ? '/api/auth/register' : '/api/auth/login'; const data = await api(endpoint, { method:'POST', body:JSON.stringify({username,password}) }); localStorage.setItem(tokenKey, data.token); $('#authDialog').close(); await start(); } catch (error) { $('#authError').textContent = error.message; } finally { $('#authSubmit').disabled = false; } });
$('#authMode').addEventListener('change', () => { const register = $('#authMode').value === 'register'; $('#authSubmit').textContent = register ? 'Create account' : 'Sign in'; $('#authPassword').autocomplete = register ? 'new-password' : 'current-password'; });
$('#profileBtn').addEventListener('click', async () => { if (!state.user) return $('#authDialog').showModal(); try { await api('/api/auth/logout',{method:'POST'}); } catch {} logout(); });
$('#chatSearch').addEventListener('click', () => $('#searchInput').focus()); $('#detailsSearch').addEventListener('click', () => $('#searchInput').focus());
$('#chatMore').addEventListener('click', () => toast('More chat actions will be added after core moderation rules are defined.')); $('#blockBtn').addEventListener('click', () => toast('Blocking will be connected to the account safety system next.')); $('#attachBtn').addEventListener('click', () => toast('Attachments are intentionally disabled until secure file storage is configured.'));
$('#loadOlder').addEventListener('click', loadOlderMessages);
async function start() { await Promise.all([loadMe(),loadChats()]); await loadPresence(); connectSocket(); }
async function autoAuth() { if (!localStorage.getItem(tokenKey)) { $('#authDialog').showModal(); return; } try { await start(); } catch (error) { localStorage.removeItem(tokenKey); $('#authError').textContent = error.message || 'Please sign in again.'; $('#authDialog').showModal(); } }
autoAuth();
