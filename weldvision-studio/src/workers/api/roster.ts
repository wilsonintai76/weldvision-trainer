/**
 * Roster API Routes — KV-backed classroom roster
 */

import { Hono } from "hono";
import type { Bindings } from "../index";

export const roster = new Hono<{ Bindings: Bindings }>();

// ── GET /api/roster — active classroom roster ─────────────────────────────────

roster.get("/", async (c) => {
  // List all roster entries from KV
  const list = await c.env.ROSTER.list({ prefix: "student:" });

  const students = await Promise.all(
    list.keys.map(async (k) => {
      const value = await c.env.ROSTER.get(k.name);
      return value ? JSON.parse(value) : null;
    })
  );

  const active = students.filter(Boolean);

  return c.json({
    students: active,
    count: active.length,
    timestamp: Date.now(),
  });
});

// ── PUT /api/roster/:seat — update student status ─────────────────────────────

roster.put("/:seat", async (c) => {
  const seat = c.req.param("seat");
  const body = await c.req.json();

  const entry = {
    seat,
    studentId: body.studentId || "",
    studentName: body.studentName || "",
    isWelding: body.isWelding ?? true,
    lastScore: body.lastScore,
    lastDefect: body.lastDefect,
    lastSeenAt: Date.now(),
  };

  await c.env.ROSTER.put(`student:${seat}`, JSON.stringify(entry), {
    expirationTtl: 300, // 5 min — students must heartbeat
  });

  return c.json({ status: "updated", entry });
});

// ── DELETE /api/roster/:seat — remove student from roster ─────────────────────

roster.delete("/:seat", async (c) => {
  const seat = c.req.param("seat");
  await c.env.ROSTER.delete(`student:${seat}`);
  return c.json({ status: "deleted" });
});
