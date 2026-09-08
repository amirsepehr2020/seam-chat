const $ = (s) => document.querySelector(s);
const state = { user: null, activeUser: null, socket: null, chats: [], messages: new Map() };
const tokenKey = 'seam_chat_token';

function toast(text) { const el = $('#toast'); el.textContent = text; el.classList.add('show'); clearTimeout(window.__toast); window.__toast = setTimeout(() => el.classList.remove('show'), 2200); }
function initials(name) { return (name || '?').slice(0, 1).toUpperCase(); }
function authHeaders() { const token = localStorage.getItem(tokenKey); return token ? { Authorization: `Bearer ${token}` } : {}; }
function api(path, options = {}) { return fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(options.headers || {}) } }).then(async r => { const data = await r.json().catch(() => ({})); if (!r.ok) throw new Error(data.error || 'Request failed'); return data; }); }

function renderChats(filter = '') {
  const q = filter.trim().toLowerCase();
  const list = $('#chatList');
  const chats = state.chats.filter(c => c.username.toLowerCase().includes(q));
  list.innerHTML = chats.length ? chats.map(c => `<button class="chat-row ${state.activeUser === c.username ? 'selected' : ''}" data-name="${escapeHtml(c.username)}"><span class="avatar avatar-purple">${escapeHtml(initials(c.username))}</span><span class="chat-copy"><strong>${escapeHtml(c.username)}</strong><small>${escapeHtml(c.lastMessage || 'No messages yet')}</small></span><span class="chat-meta"><time>${c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) : ''}</time></span></button>`).join('') : '<div class="empty-state">No conversations found.</div>';
  list.querySelectorAll('.chat-row').forEach(row => row.addEventListener('click', () => openChat(row.dataset.name)));
}
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c])); }

async function loadMe() { const data = await api('/api/me'); state.user = data.user; $('#profileBtn').textContent = initials(state.user.username); }
async function loadChats() { const data = await api('/api/chats'); state.chats = data.chats; renderChats($('#searchInput').value); }
async function openChat(username) {
  state.activeUser = username;
  $('#activeName').textContent = username; $('#detailsName').textContent = username; $('#detailsUsername').textContent = `@${username}`;
  $('#activeAvatar').textContent = initials(username); $('#detailsAvatar').textContent = initials(username);
  $('#activeStatus').textContent = 'Connected'; $('#messageInput').disabled = false; $('.send-btn').disabled = false;
  renderChats($('#searchInput').value);
  try { const data = await api(`/api/messages/${encodeURIComponent(username)}`); state.messages.set(username, data.messages); renderMessages(); } catch (e) { toast(e.message); }
}
function renderMessages() {
  const area = $('#messageArea'); const messages = state.messages.get(state.activeUser) || [];
  if (!messages.length) { area.innerHTML = '<div class="empty-conversation">No messages yet. Say hello.</div>'; return; }
  area.innerHTML = messages.map(m => `<article class="message ${m.sender === state.user.username ? 'sent' : 'received'}"><div>${m.sender !== state.user.username ? `<span class="bubble-avatar">${escapeHtml(initials(m.sender))}</span>` : ''}<p>${escapeHtml(m.body)}</p><time>${new Date(m.createdAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}</time></div></article>`).join('');
  area.scrollTop = area.scrollHeight;
}
function connectSocket() {
  const token = localStorage.getItem(tokenKey); if (!token) return;
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  state.socket = new WebSocket(`${protocol}//${location.host}/ws?token=${encodeURIComponent(token)}`);
  state.socket.onopen = () => { $('#connectionStatus').textContent = 'Real-time connection active'; };
  state.socket.onclose = () => { $('#connectionStatus').textContent = 'Disconnected'; };
  state.socket.onerror = () => { $('#connectionStatus').textContent = 'Connection error'; };
  state.socket.onmessage = event => { const message = JSON.parse(event.data); if (message.type !== 'message') return; const other = message.sender === state.user.username ? message.recipient : message.sender; const list = state.messages.get(other) || []; list.push(message); state.messages.set(other, list); if (state.activeUser === other) renderMessages(); loadChats().catch(() => {}); };
}

$('#composer').addEventListener('submit', async e => { e.preventDefault(); const input = $('#messageInput'); const body = input.value.trim(); if (!body || !state.activeUser) return; try { await api('/api/messages', { method: 'POST', body: JSON.stringify({ recipient: state.activeUser, body }) }); input.value = ''; } catch (err) { toast(err.message); } });
$('#searchInput').addEventListener('input', e => renderChats(e.target.value));
$('#newChat').addEventListener('click', () => $('#newChatDialog').showModal());
$('#newChatForm').addEventListener('submit', async e => { e.preventDefault(); const username = $('#targetUsername').value.trim(); try { const data = await api('/api/users/search?username=' + encodeURIComponent(username)); if (!data.user) throw new Error('User not found'); if (!state.chats.some(c => c.username === username)) state.chats.unshift({ username, lastMessage: '' }); renderChats(); $('#newChatDialog').close(); $('#targetUsername').value = ''; openChat(username); } catch (err) { toast(err.message); } });
$('#authForm').addEventListener('submit', async e => { e.preventDefault(); const username = $('#authUsername').value.trim(); const password = $('#authPassword').value; try { let data; try { data = await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }); } catch { data = await api('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, password }) }); } localStorage.setItem(tokenKey, data.token); $('#authDialog').close(); await start(); } catch (err) { $('#authError').textContent = err.message; } });
$('#notifyToggle').addEventListener('click', e => e.currentTarget.classList.toggle('on'));
$('#profileBtn').addEventListener('click', () => toast(state.user ? `Signed in as @${state.user.username}` : 'Not signed in'));
$('#contactsBtn').addEventListener('click', () => $('#newChatDialog').showModal());

autoAuth();
async function autoAuth() { if (!localStorage.getItem(tokenKey)) { $('#authDialog').showModal(); return; } try { await start(); } catch { localStorage.removeItem(tokenKey); $('#authDialog').showModal(); } }
async function start() { await loadMe(); await loadChats(); connectSocket(); }
