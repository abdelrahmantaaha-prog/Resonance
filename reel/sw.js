/* Resonance service worker — network-first so new deploys land immediately.
   Falls back to cache only when offline. */
const CACHE='resonance-v10';
self.addEventListener('install', e=>{ self.skipWaiting(); });
self.addEventListener('activate', e=>{
  e.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)));
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', e=>{
  const req=e.request;
  if(req.method!=='GET'){ return; }
  /* Leave cross-origin traffic alone. Nothing off-origin is ever cached here, so proxying it
     buys nothing — and it actively risks breaking the streaming server's byte-range requests,
     which is how video seeking works. Range and opaque responses are exactly the cases a
     pass-through service worker handles worst. */
  if(new URL(req.url).origin!==location.origin){ return; }
  e.respondWith((async()=>{
    try{
      const fresh=await fetch(req);
      if(new URL(req.url).origin===location.origin){
        const c=await caches.open(CACHE); c.put(req, fresh.clone());
      }
      return fresh;
    }catch(err){
      const cached=await caches.match(req);
      if(cached) return cached;
      throw err;
    }
  })());
});
