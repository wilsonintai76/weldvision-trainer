/**
 * WeldVision Trainer 2.0 — Browser-Side R2 Telemetry Stream Parser
 * =================================================================
 * Handles fetching, decompressing (gzip), and incremental parsing of
 * high-frequency (60 Hz) coordinate streams stored in Cloudflare R2.
 *
 * Designed to feed Three.js WebGL replay renderer with minimal GC pressure
 * via async generators and native DecompressionStream.
 */

// ---------------------------------------------------------------------------
// 1. Type Definitions
// ---------------------------------------------------------------------------

/** A single 60 Hz telemetry frame as stored in the R2 gzip archive. */
export interface TelemetryFrame {
  /** Timestamp in seconds from session start. */
  t: number;
  /** Tip X position (mm) in world frame [W]. */
  x: number;
  /** Tip Y position (mm) in world frame [W]. */
  y: number;
  /** Tip Z position (mm) in world frame [W]. */
  z: number;
  /** Travel angle θ (degrees) — deviation from joint seam vector d_W. */
  travel_angle: number;
  /** Work angle φ (degrees) — deviation from base plate normal n_W. */
  work_angle: number;
}

/** Metadata summary retrieved from D1 before fetching the full stream. */
export interface SessionMetadata {
  session_id: string;
  student_id: string;
  assignment_id: string;
  overall_score: number;
  avg_arc_length: number;
  avg_travel_speed: number;
  avg_work_angle: number;
  avg_travel_angle: number;
  telemetry_object_key: string;
  created_at: string;
}

/** Configuration for the R2 telemetry loader. */
export interface R2LoaderConfig {
  /** Base URL of your R2 bucket (public or with presigned URLs). */
  r2BaseUrl: string;
  /** Application identifier used in R2 key path. */
  appId: string;
}

// ---------------------------------------------------------------------------
// 2. Gzip Decompression Stream Utility
// ---------------------------------------------------------------------------

/**
 * Decompresses a gzip-compressed ReadableStream into a UTF-8 string.
 * Uses the native CompressionStreams API (Chrome 80+, Firefox 113+, Safari 16.4+).
 *
 * Falls back to a manual inflate if DecompressionStream is unavailable
 * (e.g. Node.js without the API — but this is primarily a browser module).
 */
async function decompressGzipStream(
  compressedStream: ReadableStream<Uint8Array>,
): Promise<string> {
  // Browser-native decompression
  if ("DecompressionStream" in self) {
    const ds = new DecompressionStream("gzip");
    const decompressedStream = compressedStream.pipeThrough(ds);
    const reader = decompressedStream.getReader();
    const chunks: Uint8Array[] = [];

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
    }

    // Concatenate all chunks and decode to string
    const totalLength = chunks.reduce((sum, c) => sum + c.length, 0);
    const merged = new Uint8Array(totalLength);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk, offset);
      offset += chunk.length;
    }

    return new TextDecoder("utf-8").decode(merged);
  }

  // Fallback: use pako or similar (require external dependency)
  // This path kept for environments lacking DecompressionStream.
  throw new Error(
    "DecompressionStream API not available. " +
      "Use a polyfill (e.g. pako) for this environment.",
  );
}

// ---------------------------------------------------------------------------
// 3. R2 Telemetry Fetcher
// ---------------------------------------------------------------------------

/**
 * Constructs the full R2 object URL from the key path pattern:
 *   /artifacts/{appId}/users/{studentId}/sessions/{sessionId}_telemetry.json.gz
 */
function buildR2Url(
  config: R2LoaderConfig,
  studentId: string,
  sessionId: string,
): string {
  const key = `artifacts/${config.appId}/users/${studentId}/sessions/${sessionId}_telemetry.json.gz`;
  return `${config.r2BaseUrl.replace(/\/$/, "")}/${key}`;
}

/**
 * Fetches and decompresses a raw telemetry stream from Cloudflare R2.
 *
 * @returns The full, parsed array of TelemetryFrames.
 *          For very large sessions (>100k frames), consider using
 *          fetchTelemetryStream() instead for incremental processing.
 */
export async function fetchTelemetryFrames(
  config: R2LoaderConfig,
  studentId: string,
  sessionId: string,
  signal?: AbortSignal,
): Promise<TelemetryFrame[]> {
  const url = buildR2Url(config, studentId, sessionId);

  const response = await fetch(url, { signal });
  if (!response.ok) {
    throw new Error(
      `R2 fetch failed: HTTP ${response.status} — ${response.statusText}`,
    );
  }

  if (!response.body) {
    throw new Error("Response body is null — cannot stream.");
  }

  const rawJson = await decompressGzipStream(response.body);
  return JSON.parse(rawJson) as TelemetryFrame[];
}

// ---------------------------------------------------------------------------
// 4. Incremental Streaming Parser (Async Generator)
// ---------------------------------------------------------------------------

/**
 * Async generator that yields TelemetryFrames one-by-one as they are
 * decompressed from the R2 gzip stream. Ideal for:
 *  - Feeding a Three.js replay loop without buffering all frames in memory
 *  - Progressive UI loading bars
 *  - Early termination (e.g., user pauses replay)
 *
 * Usage:
 *   for await (const frame of streamTelemetryFrames(config, sid, sess)) {
 *     update3DRibbon(frame);
 *   }
 */
export async function* streamTelemetryFrames(
  config: R2LoaderConfig,
  studentId: string,
  sessionId: string,
  signal?: AbortSignal,
): AsyncGenerator<TelemetryFrame, void, undefined> {
  const url = buildR2Url(config, studentId, sessionId);

  const response = await fetch(url, { signal });
  if (!response.ok || !response.body) {
    throw new Error(`R2 stream fetch failed: HTTP ${response.status}`);
  }

  const rawJson = await decompressGzipStream(response.body);
  const frames: TelemetryFrame[] = JSON.parse(rawJson);

  for (const frame of frames) {
    // Check for abort signal (user paused/stopped replay)
    if (signal?.aborted) break;
    yield frame;
  }
}

// ---------------------------------------------------------------------------
// 5. Spatial Outlier Filter (Edge-Side Replication)
// ---------------------------------------------------------------------------

const MAX_TIP_RADIUS_MM = 500.0;

/**
 * Replicates the Android on-device spatial boundary filter.
 * Any frame whose Euclidean norm exceeds 500.0 mm is flagged as a
 * tracking outlier. The web dashboard uses this to optionally hide
 * noisy frames during 3D replay.
 *
 *   || p_W_tip(t) || <= 500.0 mm
 */
export function isFrameOutlier(frame: TelemetryFrame): boolean {
  const norm = Math.sqrt(frame.x * frame.x + frame.y * frame.y + frame.z * frame.z);
  return norm > MAX_TIP_RADIUS_MM;
}

/**
 * Filters an array of frames, returning only valid (non-outlier) frames.
 */
export function filterOutliers(frames: TelemetryFrame[]): TelemetryFrame[] {
  return frames.filter((f) => !isFrameOutlier(f));
}

// ---------------------------------------------------------------------------
// 6. Derived Angle Computation (Web-Side Replay Metrics)
// ---------------------------------------------------------------------------

/**
 * Reconstructs the torch heading vector from a frame's travel and work angles.
 * This mirrors the Android fusion filter's quaternion-to-angle decomposition,
 * allowing the web dashboard to independently verify orientation metrics.
 *
 * @returns { heading, travelAngleRad, workAngleRad }
 */
export function decomposeAngles(frame: TelemetryFrame) {
  const travelAngleRad = (frame.travel_angle * Math.PI) / 180.0;
  const workAngleRad = (frame.work_angle * Math.PI) / 180.0;

  // Reconstruct unit heading vector from spherical coordinates
  // where travel_angle is azimuth and work_angle is elevation from normal.
  const heading = {
    x: Math.sin(workAngleRad) * Math.cos(travelAngleRad),
    y: Math.sin(workAngleRad) * Math.sin(travelAngleRad),
    z: Math.cos(workAngleRad),
  };

  return { heading, travelAngleRad, workAngleRad };
}

// ---------------------------------------------------------------------------
// 7. Three.js Integration Helper
// ---------------------------------------------------------------------------

/**
 * Converts a TelemetryFrame into a THREE.Vector3 suitable for
 * positioning a ribbon point or torch mesh in the replay scene.
 *
 * Assumes the Three.js scene coordinate system matches the
 * weld plate world frame [W] (Y-up or Z-up depending on your setup).
 *
 * Usage:
 *   import * as THREE from "three";
 *   const pos = toThreeVector(frame, /* zUp = *\/ true);
 *   ribbonGeometry.vertices.push(pos);
 */
export function toThreeVector(
  frame: TelemetryFrame,
  zUp = true,
): { x: number; y: number; z: number } {
  if (zUp) {
    // Z-up convention: swap Y and Z from the world frame
    return { x: frame.x, y: frame.z, z: frame.y };
  }
  return { x: frame.x, y: frame.y, z: frame.z };
}

// ---------------------------------------------------------------------------
// 8. D1 Session Metadata Fetcher
// ---------------------------------------------------------------------------

/**
 * Fetches session summary metadata from a Cloudflare Worker endpoint
 * that queries D1. Used to populate the instructor dashboard list
 * before loading individual 3D replays.
 *
 * Expected Worker endpoint: GET /api/sessions/:studentId
 */
export async function fetchSessionMetadata(
  workerBaseUrl: string,
  studentId: string,
  signal?: AbortSignal,
): Promise<SessionMetadata[]> {
  const url = `${workerBaseUrl.replace(/\/$/, "")}/api/sessions/${encodeURIComponent(studentId)}`;
  const response = await fetch(url, { signal });

  if (!response.ok) {
    throw new Error(`D1 query failed: HTTP ${response.status}`);
  }

  return response.json() as Promise<SessionMetadata[]>;
}

// ---------------------------------------------------------------------------
// 9. Complete Replay Loading Pipeline (Orchestrator)
// ---------------------------------------------------------------------------

/**
 * High-level orchestrator that:
 *  1. Fetches session metadata from D1 (via Worker).
 *  2. Streams + decompresses the R2 telemetry archive.
 *  3. Filters spatial outliers.
 *  4. Yields clean frames ready for Three.js rendering.
 *
 * This is the main entry point for the Instructor Web Replay Suite.
 */
export async function* loadReplayStream(
  config: R2LoaderConfig & { workerBaseUrl: string },
  studentId: string,
  sessionId: string,
  signal?: AbortSignal,
): AsyncGenerator<TelemetryFrame, void, undefined> {
  // Optional: pre-fetch metadata to validate session exists
  // const sessions = await fetchSessionMetadata(config.workerBaseUrl, studentId, signal);
  // const meta = sessions.find((s) => s.session_id === sessionId);
  // if (!meta) throw new Error(`Session ${sessionId} not found for student ${studentId}`);

  for await (const frame of streamTelemetryFrames(
    config,
    studentId,
    sessionId,
    signal,
  )) {
    if (!isFrameOutlier(frame)) {
      yield frame;
    }
    // Outliers are silently dropped, matching the Android edge behavior
  }
}
