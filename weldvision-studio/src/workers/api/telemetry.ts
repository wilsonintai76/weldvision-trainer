/**
 * Telemetry API Routes — R2-backed coordinate stream storage + AI analysis
 */

import { Hono } from "hono";
import { z } from "zod";
import { zValidator } from "@hono/zod-validator";
import type { Bindings } from "../index";

export const telemetry = new Hono<{ Bindings: Bindings }>();

// ── Schemas ──────────────────────────────────────────────────────────────────

const analyzeSchema = z.object({
  sessionId: z.string().min(1),
  telemetryKey: z.string().min(1),
  analysisType: z.enum(["defect", "distortion", "full"]).default("full"),
});

// ── GET /api/telemetry/:key — fetch telemetry archive (R2) ────────────────────

telemetry.get("/:key", async (c) => {
  const key = c.req.param("key");

  try {
    const object = await c.env.TELEMETRY.get(key);
    if (!object) return c.json({ error: "Telemetry archive not found" }, 404);

    const data = await object.arrayBuffer();

    return new Response(data, {
      status: 200,
      headers: {
        "Content-Type": object.httpMetadata?.contentType || "application/gzip",
        "Content-Disposition": `attachment; filename="${key}"`,
        "Cache-Control": "public, max-age=3600",
      },
    });
  } catch (err) {
    console.error("R2 fetch error:", err);
    return c.json({ error: "Failed to fetch telemetry" }, 500);
  }
});

// ── POST /api/telemetry/upload — upload telemetry archive to R2 ───────────────

telemetry.post("/upload", async (c) => {
  const formData = await c.req.formData();
  const file = formData.get("file") as File | null;
  const sessionId = formData.get("sessionId") as string | null;

  if (!file) return c.json({ error: "No file provided" }, 400);
  if (!sessionId) return c.json({ error: "No sessionId provided" }, 400);

  const key = `sessions/${sessionId}/telemetry.json.gz`;

  await c.env.TELEMETRY.put(key, file.stream(), {
    httpMetadata: {
      contentType: "application/gzip",
      cacheControl: "public, max-age=86400",
    },
  });

  // Update session with telemetry reference
  await c.env.DB.prepare(
    "UPDATE sessions SET telemetry_object_key = ?, updated_at = unixepoch() WHERE id = ?"
  ).bind(key, sessionId).run();

  return c.json({ key, status: "uploaded" }, 201);
});

// ── POST /api/telemetry/analyze — AI-powered defect detection ─────────────────

telemetry.post("/analyze", zValidator("json", analyzeSchema), async (c) => {
  const { sessionId, telemetryKey, analysisType } = c.req.valid("json");

  try {
    // Fetch telemetry from R2
    const object = await c.env.TELEMETRY.get(telemetryKey);
    if (!object) return c.json({ error: "Telemetry not found" }, 404);

    const raw = await object.text();

    // Run AI inference via Workers AI
    const prompt = `Analyze the following GMAW welding telemetry data for defects and quality issues.
Return a JSON object with:
- defects: array of detected issues (e.g. "porosity", "undercut", "lack_of_fusion", "spatter")
- severity: "low" | "medium" | "high"
- recommendations: array of coaching suggestions
- quality_score: 0-100

Telemetry data (first 500 chars):
${raw.slice(0, 500)}`;

    const aiResponse = await c.env.AI.run("@cf/meta/llama-3.3-70b-instruct", {
      messages: [{ role: "user", content: prompt }],
      max_tokens: 500,
    });

    // Parse AI response
    let analysis: any;
    try {
      const text = (aiResponse as any).response || "";
      const jsonMatch = text.match(/\{[\s\S]*\}/);
      analysis = jsonMatch ? JSON.parse(jsonMatch[0]) : { raw: text };
    } catch {
      analysis = { raw: (aiResponse as any).response || "" };
    }

    // Store analysis result in D1
    await c.env.DB.prepare(
      `UPDATE sessions SET
        defect_count = ?,
        porosity_risk = ?,
        coaching_phrase = ?,
        updated_at = unixepoch()
       WHERE id = ?`
    ).bind(
      analysis.defects?.length || 0,
      analysis.severity || "unknown",
      analysis.recommendations?.[0] || "",
      sessionId
    ).run();

    return c.json({ sessionId, analysis, status: "completed" });
  } catch (err) {
    console.error("AI analysis error:", err);
    return c.json({ error: "Analysis failed" }, 500);
  }
});
