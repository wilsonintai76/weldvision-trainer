-- ============================================================================
-- WeldVision Trainer 2.0 — D1 Database Schema
-- ============================================================================

-- Student roster (synced from Firebase Auth or manual import)
CREATE TABLE IF NOT EXISTS students (
  id            TEXT PRIMARY KEY,          -- UUID
  name          TEXT NOT NULL,
  email         TEXT,
  matric_no     TEXT,
  level         INTEGER DEFAULT 1,
  created_at    INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at    INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Welding sessions — one per student per practice run
CREATE TABLE IF NOT EXISTS sessions (
  id              TEXT PRIMARY KEY,        -- UUID
  student_id      TEXT NOT NULL REFERENCES students(id),
  seat_identifier TEXT NOT NULL,           -- e.g. "seat_03"
  process         TEXT DEFAULT 'GMAW',     -- welding process
  material        TEXT DEFAULT 'Mild Steel',
  joint_type      TEXT DEFAULT 'T-Joint',
  status          TEXT DEFAULT 'in_progress', -- in_progress | completed | aborted

  -- Scores (0-100)
  overall_score       INTEGER,
  arc_length_score    INTEGER,
  travel_speed_score  INTEGER,
  angle_score         INTEGER,
  defect_score        INTEGER,

  -- Metrics
  arc_length_stability       REAL,
  travel_speed_uniformity    REAL,
  angle_orientation_stability REAL,
  defect_count               INTEGER DEFAULT 0,
  porosity_risk              TEXT,        -- low | medium | high

  -- Timing
  weld_time_seconds  REAL DEFAULT 0,
  started_at         INTEGER NOT NULL DEFAULT (unixepoch()),
  completed_at       INTEGER,

  -- Coaching
  coaching_phrase    TEXT,

  -- R2 telemetry reference
  telemetry_object_key TEXT,              -- R2 key for gzip archive

  created_at  INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at  INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_sessions_student ON sessions(student_id);
CREATE INDEX IF NOT EXISTS idx_sessions_status  ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_sessions_started ON sessions(started_at DESC);

-- Classroom roster cache (mirrored in KV for real-time reads)
CREATE TABLE IF NOT EXISTS roster_cache (
  seat_identifier TEXT PRIMARY KEY,
  student_id      TEXT NOT NULL,
  student_name    TEXT NOT NULL,
  is_welding      INTEGER DEFAULT 0,      -- boolean: 0=idle, 1=welding
  last_score      INTEGER,
  last_defect     TEXT,
  last_seen_at    INTEGER NOT NULL DEFAULT (unixepoch())
);
