const CACHE='birdquest-download-v1';
self.addEventListener('install',event=>event.waitUntil((async()=>{const cache=await caches.open(CACHE);const manifest=await fetch('./model-assets.json').then(r=>r.json());await cache.addAll(['./','./index.html','./style.css','./app.js','./recognition.js','./birdnet-worker.js','./bird-placeholder.svg','./manifest.webmanifest','./model-assets.json',...manifest.assets.map(a=>'./'+a.file)]);await self.skipWaiting()})()));
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('fetch',event=>{if(event.request.method!=='GET'||new URL(event.request.url).origin!==self.location.origin)return;event.respondWith(caches.match(event.request).then(hit=>hit||fetch(event.request)))});
