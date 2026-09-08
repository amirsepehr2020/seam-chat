const $ = (s) => document.querySelector(s);
const state = { user: null, activeUser: null, socket: null, reconnectTimer: null, reconnectAttempt: 0, intentionalClose: false, chats: [], messages: new Map(), onlineUsers: new Set() };
const tokenKey = 'seam_chat_token';
const apiBase = (window.SEAM_API_BASE || location.origin).replace(/\/$/, '');

function wsUrl() { const url = new URL(apiBase + '/ws'); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'; url.searchParams.set('token', localStorage.getItem(tokenKey) || ''); return url.toString(); }
function toast(text) { const el = $('#toast'); el.textContent = text; el.classList.add('show'); clearTimeout(window.__toast); window.__toast = setTimeout(() => el.classList.remove('show'), 2600); }
function initials(name) { return (name || '?').slice(0, 1).toUpperCase(); }
function authHeaders() { const token = localStorage.getItem(tokenKey); return token ? { Authorization: `Bearer ${token}` } : {}; }
async function api(path, options = {}) { const response = await fetch(`${apiBase}${path}`, { ...options, headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...authHeaders(), ...(options.headers || {}) } }); const data = await response.json().catch(() => ({})); if (!response.ok) { const error = new Error(data.error || `Request failed (${response.status})`); error.status = response.status; throw error; } return data; }
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;' }[c])); }

function renderChats(filter = '') {
  const q = filter.trim().toLowerCase(); const chats = state.chats.filter(c => c.username.toLowerCase().includes(q)); const list = $('#chatList');
  list.innerHTML = chats.length ? chats.map(c => `<button class="chat-row ${state.activeUser === c.username ? 'selected' : ''}" data-name="${escapeHtml(c.username)}"><span class="avatar avatar-purple">${escapeHtml(initials(c.username))}</span><span class="chat-copy"><strong>${escapeHtml(c.username)}</strong><small>${escapeHtml(c.lastMessage || 'No messages yet')}</small></span><span class="chat-meta"><time>${c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) : ''}</time></span></button>`).join('') : '<div class="empty-state">No conversations yet.</div>';
  list.querySelectorAll('.chat-row').forEach(row => row.addEventListener('click', () => openChat(row.dataset.name)));
}

function renderMessages() {
  const area = $('#messageArea'); const messages = state.messages.get(state.activeUser) || [];
  if (!messages.length) { area.innerHTML = '<div class="empty-conversation">No messages yet. Say hello.</div>'; return; }
  area.innerHTML = messages.map(m => `<article class="message ${m.sender === state.user.username ? 'sent' : 'received'}"><div>${m.sender !== state.user.username ? `<span class="bubble-avatar">${escapeHtml(initials(m.sender))}</span>` : ''}<p>${escapeHtml(m.body)}</p><time>${new Date(m.createdAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}</time></div></article>`).join('');
  area.scrollTop = area.scrollHeight;
}

function addMessage(message) {
  const other = message.sender === state.user.username ? message.recipient : message.sender; const list = state.messages.get(other) || [];
  if (!list.some(item => String(item.id) === String(message.id))) list.push(message); state.messages.set(other, list);
  const existing = state.chats.find(c => c.username === other); if (existing) { existing.lastMessage = message.body; existing.lastMessageAt = message.createdAt; } else state.chats.unshift({ username: other, lastMessage: message.body, lastMessageAt: message.createdAt });
  state.chats.sort((a,b) => new Date(b.lastMessageAt || 0) - new Date(a.lastMessageAt || 0)); renderChats($('#searchInput').value); if (state.activeUser === other) renderMessages();
}
async function loadMe() { const data = await api('/api/me'); state.user = data.user; $('#profileBtn').textContent = initials(state.user.username); }
async function loadChats() { const data = await api('/api/chats'); state.chats = data.chats; renderChats($('#searchInput').value); }
async function openChat(username) {
  if (!username || username === state.user.username) return toast('You cannot start a chat with yourself.'); state.activeUser = username;
  $('#activeName').textContent = username; $('#detailsName').textContent = username; $('#detailsUsername').textContent = `@${username}`; $('#activeAvatar').textContent = initials(username); $('#detailsAvatar').textContent = initials(username); updateActiveStatus(); $('#messageInput').disabled = false; $('.send-btn').disabled = false; renderChats($('#searchInput').value);
  try { const data = await api(`/api/messages/${encodeURIComponent(username)}`); state.messages.set(username, data.messages); renderMessages(); } catch (error) { toast(error.status === 404 ? 'User not found.' : error.message); }
}
function updateActiveStatus() { if (state.activeUser) $('#activeStatus').textContent = state.onlineUsers.has(state.activeUser) ? 'Online' : 'Offline'; }
function setConnectionStatus(text) { $('#connectionStatus').textContent = text; }
function scheduleReconnect() { if (state.intentionalClose || !localStorage.getItem(tokenKey) || state.reconnectTimer) return; const delay = Math.min(15000, 1000 * 2 ** state.reconnectAttempt); state.reconnectAttempt += 1; setConnectionStatus(`Reconnecting in ${Math.ceil(delay/1000)}s…`); state.reconnectTimer = setTimeout(() => { state.reconnectTimer = null; connectSocket(); }, delay); }
function connectSocket() {
  const token = localStorage.getItem(tokenKey); if (!token) return; if (state.socket && (state.socket.readyState === WebSocket.OPEN || state.socket.readyState === WebSocket.CONNECTING)) return; state.intentionalClose = false; setConnectionStatus('Connecting…');
  let socket; try { socket = new WebSocket(wsUrl()); } catch { scheduleReconnect(); return; } state.socket = socket;
  socket.onopen = () => { state.reconnectAttempt = 0; setConnectionStatus('Real-time connection active'); };
  socket.onmessage = event => { try { const message = JSON.parse(event.data); if (message.type === 'message') addMessage(message); if (message.type === 'presence') { if (message.online) state.onlineUsers.add(message.username); else state.onlineUsers.delete(message.username); updateActiveStatus(); } } catch {} };
  socket.onerror = () => setConnectionStatus('Connection error');
  socket.onclose = () => { if (state.socket === socket) state.socket = null; if (!state.intentionalClose) scheduleReconnect(); else setConnectionStatus('Disconnected'); };
}
function logout() {
  state.intentionalClose = true; clearTimeout(state.reconnectTimer); state.reconnectTimer = null; if (state.socket) state.socket.close(1000, 'logout'); state.socket = null; localStorage.removeItem(tokenKey); state.user = null; state.activeUser = null; state.chats = []; state.messages.clear(); state.onlineUsers.clear(); $('#profileBtn').textContent = '?'; $('#activeName').textContent = 'Select a chat'; $('#activeStatus').textContent = '—'; $('#detailsName').textContent = 'No chat selected'; $('#detailsUsername').textContent = '—'; $('#messageInput').disabled = true; $('.send-btn').disabled = true; $('#messageArea').innerHTML = '<div class="empty-conversation">Sign in to start messaging.</div>'; renderChats(); $('#authDialog').showModal();
}

$('#composer').addEventListener('submit', async e => { e.preventDefault(); const input = $('#messageInput'); const body = input.value.trim(); if (!body || !state.activeUser) return; const button = $('.send-btn'); button.disabled = true; try { const data = await api('/api/messages', { method:'POST', body:JSON.stringify({ recipient:state.activeUser, body }) }); addMessage(data.message); input.value = ''; } catch (error) { if (error.status === 401) logout(); else toast(error.message); } finally { if (state.activeUser) button.disabled = false; input.focus(); } });
$('#searchInput').addEventListener('input', e => renderChats(e.target.value));
$('#newChat').addEventListener('click', () => $('#newChatDialog').showModal());
$('#contactsBtn').addEventListener('click', () => $('#newChatDialog').showModal());
$('#newChatForm').addEventListener('submit', async e => { e.preventDefault(); const username = $('#targetUsername').value.trim(); try { const data = await api(`/api/users/search?username=${encodeURIComponent(username)}`); if (!data.user) throw new Error('User not found.'); $('#newChatDialog').close(); $('#targetUsername').value = ''; await openChat(data.user.username); } catch (error) { toast(error.message); } });
$('#authForm').addEventListener('submit', async e => { e.preventDefault(); const username = $('#authUsername').value.trim(); const password = $('#authPassword').value; const mode = $('#authMode').value; $('#authError').textContent = ''; $('#authSubmit').disabled = true; try { const endpoint = mode === 'register' ? '/api/auth/register' : '/api/auth/login'; const data = await api(endpoint, { method:'POST', body:JSON.stringify({username,password}) }); localStorage.setItem(tokenKey, data.token); $('#authDialog').close(); await start(); } catch (error) { $('#authError').textContent = error.message; } finally { $('#authSubmit').disabled = false; } });
$('#authMode').addEventListener('change', () => { const register = $('#authMode').value === 'register'; $('#authSubmit').textContent = register ? 'Create account' : 'Sign in'; $('#authPassword').autocomplete = register ? 'new-password' : 'current-password'; });
$('#profileBtn').addEventListener('click', () => { if (!state.user) return $('#authDialog').showModal(); if (confirm(`Sign out @${state.user.username}?`)) logout(); });
$('#chatSearch').addEventListener('click', () => $('#searchInput').focus()); $('#detailsSearch').addEventListener('click', () => $('#searchInput').focus());
$('#chatMore').addEventListener('click', () => toast('Chat actions are not available yet.')); $('#blockBtn').addEventListener('click', () => toast('Blocking is not available yet.')); $('#attachBtn').addEventListener('click', () => toast('Attachments are not available yet.'));
async function start() { await loadMe(); await loadChats(); connectSocket(); }
async function autoAuth() { if (!localStorage.getItem(tokenKey)) { $('#authDialog').showModal(); return; } try { await start(); } catch (error) { localStorage.removeItem(tokenKey); $('#authError').textContent = error.message || 'Please sign in again.'; $('#authDialog').showModal(); } }
autoAuth();
