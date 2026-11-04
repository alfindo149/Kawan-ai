const CACHE_NAME = "gia-shell-v15";
const APP_SHELL = ["./", "./index.html", "./manifest.webmanifest", "./icon.svg", "./gia-avatar-real.jpg"];

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

// Network-first for app pages and assets; never cache auth, API, or external requests.
self.addEventListener("fetch", event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  if (url.pathname.includes("/functions/") || url.pathname.includes("/auth/")) return;

  const appBase = new URL(self.registration.scope);
  const isAppShell = url.pathname === appBase.pathname ||
    url.pathname === appBase.pathname + "index.html" ||
    url.pathname === appBase.pathname + "manifest.webmanifest" ||
    url.pathname === appBase.pathname + "icon.svg" ||
    url.pathname === appBase.pathname + "gia-avatar-real.jpg";
  if (!isAppShell && request.mode !== "navigate") return;

  event.respondWith(
    fetch(request)
      .then(response => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
        }
        return response;
      })
      .catch(async () => {
        const cached = await caches.match(request);
        if (cached) return cached;
        if (request.mode === "navigate") return caches.match("./index.html");
        throw new Error("Offline and resource is not cached");
      })
  );
});
