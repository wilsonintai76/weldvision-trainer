/**
 * WeldVision Trainer 2.0 — Service Worker
 * ========================================
 *
 * Enables offline PWA caching for the Classroom Command Center.
 * Caches the core HTML, JS, CSS, and CDN dependencies so the
 * dashboard remains functional even during brief network drops.
 *
 * Strategy: Cache-First for CDN assets, Network-First for local files.
 */

const CACHE_NAME = "weldvision-cc-v1";

const PRECACHE_URLS = [
  "./classroom-command-center.html",
  "./mqtt-client.ts",
  "./telemetry-stream.ts",
  "./manifest.json",
  "https://unpkg.com/three@0.160.0/build/three.module.js",
  "https://unpkg.com/mqtt@5.10.1/dist/mqtt.min.js",
];

// ── Install: Pre-cache critical assets ───────────────

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log("[SW] Precaching assets...");
      return cache.addAll(PRECACHE_URLS).catch((err) => {
        console.warn("[SW] Precache partial failure:", err);
      });
    })
  );
  self.skipWaiting();
});

// ── Activate: Clean old caches ───────────────────────

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
      );
    })
  );
  self.clients.claim();
});

// ── Fetch: Cache-first strategy ──────────────────────

self.addEventListener("fetch", (event) => {
  // Only handle GET requests
  if (event.request.method !== "GET") return;

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) {
        // Return cached version immediately
        // Then update cache in background
        const fetchPromise = fetch(event.request).then((response) => {
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, clone);
            });
          }
          return response;
        }).catch(() => cached);
        return cached;
      }

      // Not in cache — fetch from network
      return fetch(event.request).then((response) => {
        if (response.ok) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, clone);
          });
        }
        return response;
      });
    })
  );
});
