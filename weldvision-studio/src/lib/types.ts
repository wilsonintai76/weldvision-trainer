/**
 * WeldVision Trainer 2.0 — Type Definitions
 */

// ── Student & Roster ──────────────────────────────────────────────────────────

export interface StudentMeta {
  name: string;
  seat: string;
  studentId: string;
}

export interface SpatialData {
  x: number;
  y: number;
  z: number;
}

export interface AngleData {
  work: number;
  travel: number;
}

export interface TravelData {
  speed: number;
  progress: number;
  distanceMm: number;
}

/** A single 60 Hz telemetry frame from one student device. */
export interface StudentTelemetryFrame {
  meta: StudentMeta;
  spatial: SpatialData;
  angles: AngleData;
  travel: TravelData;
  timestamp: number;
}

/** Per-student roster entry maintained in the dashboard. */
export interface RosterEntry {
  name: string;
  seat: string;
  studentId: string;
  isWelding: boolean;
  score: number;
  defect: string;
  latestFrame: StudentTelemetryFrame | null;
  frameHistory: StudentTelemetryFrame[];
}

export type ClassroomRoster = Record<string, RosterEntry>;

// ── Telemetry (R2 archive format) ─────────────────────────────────────────────

export interface TelemetryFrame {
  t: number;
  x: number;
  y: number;
  z: number;
  travel_angle: number;
  work_angle: number;
}

// ── API Types (matches Hono routes) ───────────────────────────────────────────

export interface SessionRecord {
  id: string;
  student_id: string;
  seat_identifier: string;
  process: string;
  material: string;
  joint_type: string;
  status: "in_progress" | "completed" | "aborted";
  overall_score: number | null;
  arc_length_score: number | null;
  travel_speed_score: number | null;
  angle_score: number | null;
  defect_score: number | null;
  arc_length_stability: number | null;
  travel_speed_uniformity: number | null;
  angle_orientation_stability: number | null;
  defect_count: number;
  porosity_risk: string | null;
  weld_time_seconds: number;
  started_at: number;
  completed_at: number | null;
  coaching_phrase: string | null;
  telemetry_object_key: string | null;
}

export interface RosterStudent {
  seat: string;
  studentId: string;
  studentName: string;
  isWelding: boolean;
  lastScore?: number;
  lastDefect?: string;
  lastSeenAt: number;
}

export interface AIAnalysis {
  defects: string[];
  severity: "low" | "medium" | "high";
  recommendations: string[];
  quality_score: number;
}

// ── Dashboard State ───────────────────────────────────────────────────────────

export type DashboardView = "roster" | "focus";
