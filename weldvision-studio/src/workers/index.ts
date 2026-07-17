/**
 * WeldVision Trainer 2.0 — Hono Worker API
 * =========================================
 *
 * Cloudflare Worker exposing a type-safe RPC API for the React dashboard.
 *
 * Endpoints:
 *   GET  /api/roster              — active classroom roster (KV-backed)
 *   GET  /api/sessions            — list sessions (D1)
 *   GET  /api/sessions/:id        — session detail
 *   POST /api/sessions            — create session
 *   PUT  /api/sessions/:id/score  — update score
 *   GET  /api/telemetry/:key      — fetch telemetry archive (R2)
 *   POST /api/telemetry/analyze   — AI defect analysis (Workers AI)
 *
 * Stack: Hono 4.x + Zod validation + D1 + R2 + KV + Workers AI
 */

import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { sessions } from "./api/sessions";
import { telemetry } from "./api/telemetry";
import { roster } from "./api/roster";

// ── Environment Bindings ────────────────────────────────────────────────────

export type Bindings = {
  DB: D1Database;
  TELEMETRY: R2Bucket;
  ROSTER: KVNamespace;
  AI: Ai;
};

// ── App ──────────────────────────────────────────────────────────────────────

const app = new Hono<{ Bindings: Bindings }>();

// Middleware
app.use("*", cors({ origin: "*", allowMethods: ["GET", "POST", "PUT", "DELETE"] }));
app.use("*", logger());

// Health check
app.get("/api/health", (c) =>
  c.json({ status: "ok", timestamp: Date.now(), version: "2.0.0" })
);

// Route groups
app.route("/api/roster", roster);
app.route("/api/sessions", sessions);
app.route("/api/telemetry", telemetry);

// 404
app.notFound((c) => c.json({ error: "Not found" }, 404));

// Error handler
app.onError((err, c) => {
  console.error("Unhandled error:", err);
  return c.json({ error: "Internal server error" }, 500);
});

export default app;

// ── Hono RPC type export ────────────────────────────────────────────────────
export type AppType = typeof app;
