/**
 * Session API Routes — D1-backed CRUD for welding sessions
 */

import { Hono } from "hono";
import { z } from "zod";
import { zValidator } from "@hono/zod-validator";
import type { Bindings } from "../index";

export const sessions = new Hono<{ Bindings: Bindings }>();

// ── Schemas ──────────────────────────────────────────────────────────────────

const createSessionSchema = z.object({
  studentId: z.string().min(1),
  seatIdentifier: z.string().min(1),
  process: z.string().default("GMAW"),
  material: z.string().default("Mild Steel"),
  jointType: z.string().default("T-Joint"),
});

const updateScoreSchema = z.object({
  overallScore: z.number().min(0).max(100).optional(),
  arcLengthScore: z.number().min(0).max(100).optional(),
  travelSpeedScore: z.number().min(0).max(100).optional(),
  angleScore: z.number().min(0).max(100).optional(),
  defectScore: z.number().min(0).max(100).optional(),
  arcLengthStability: z.number().optional(),
  travelSpeedUniformity: z.number().optional(),
  angleOrientationStability: z.number().optional(),
  defectCount: z.number().int().optional(),
  porosityRisk: z.enum(["low", "medium", "high"]).optional(),
  coachingPhrase: z.string().optional(),
  status: z.enum(["in_progress", "completed", "aborted"]).optional(),
  telemetryObjectKey: z.string().optional(),
});

// ── GET /api/sessions — list sessions ─────────────────────────────────────────

sessions.get("/", async (c) => {
  const studentId = c.req.query("studentId");
  const status = c.req.query("status");
  const limit = Math.min(parseInt(c.req.query("limit") || "50"), 100);

  let sql = "SELECT * FROM sessions";
  const conditions: string[] = [];
  const params: any[] = [];

  if (studentId) {
    conditions.push("student_id = ?");
    params.push(studentId);
  }
  if (status) {
    conditions.push("status = ?");
    params.push(status);
  }
  if (conditions.length > 0) {
    sql += " WHERE " + conditions.join(" AND ");
  }
  sql += " ORDER BY started_at DESC LIMIT ?";
  params.push(limit);

  const result = await c.env.DB.prepare(sql).bind(...params).all();
  return c.json({ sessions: result.results, count: result.results.length });
});

// ── GET /api/sessions/:id — single session ────────────────────────────────────

sessions.get("/:id", async (c) => {
  const id = c.req.param("id");
  const result = await c.env.DB.prepare(
    "SELECT * FROM sessions WHERE id = ?"
  ).bind(id).first();

  if (!result) return c.json({ error: "Session not found" }, 404);
  return c.json(result);
});

// ── POST /api/sessions — create new session ───────────────────────────────────

sessions.post("/", zValidator("json", createSessionSchema), async (c) => {
  const body = c.req.valid("json");
  const id = crypto.randomUUID();

  await c.env.DB.prepare(`
    INSERT INTO sessions (id, student_id, seat_identifier, process, material, joint_type)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(id, body.studentId, body.seatIdentifier, body.process, body.material, body.jointType).run();

  // Update roster cache
  await c.env.ROSTER.put(
    `student:${body.seatIdentifier}`,
    JSON.stringify({
      seat: body.seatIdentifier,
      studentId: body.studentId,
      isWelding: true,
      lastSeenAt: Date.now(),
    }),
    { expirationTtl: 300 } // 5 min TTL
  );

  return c.json({ id, status: "created" }, 201);
});

// ── PUT /api/sessions/:id/score — finalize session with scores ────────────────

sessions.put("/:id/score", zValidator("json", updateScoreSchema), async (c) => {
  const id = c.req.param("id");
  const body = c.req.valid("json");

  // Build dynamic UPDATE
  const fields: string[] = [];
  const values: any[] = [];

  for (const [key, value] of Object.entries(body)) {
    if (value !== undefined) {
      const col = key.replace(/([A-Z])/g, "_$1").toLowerCase();
      fields.push(`${col} = ?`);
      values.push(value);
    }
  }
  fields.push("updated_at = unixepoch()");
  values.push(id);

  await c.env.DB.prepare(
    `UPDATE sessions SET ${fields.join(", ")} WHERE id = ?`
  ).bind(...values).run();

  return c.json({ id, status: "updated" });
});
