const CACHE_NAME = 'seam-chat-v3';
const APP_SHELL = ['/', '/index.html', '/styles.css', '/history.css', '/mobile.css', '/mobile.js', '/app.js', '/manifest.webmanifest', '/assets/seam-chat-logo.svg'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))));
  self.clients.claim();
});

async function navigationResponse(request) {
  try {
    const network = await fetch(request);
    const html = await network.text();
    const enhanced = html.replace('</body>', '<script src="/mobile.js"></script></body>');
    return new Response(enhanced, { status: network.status, statusText: network.statusText, headers: network.headers });
  } catch {
    return caches.match('/index.html');
  }
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(navigationResponse(request));
    return;
  }

  event.respondWith(fetch(request).then((response) => {
    const copy = response.clone();
    caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
    return response;
  }).catch(() => caches.match(request)));
});
