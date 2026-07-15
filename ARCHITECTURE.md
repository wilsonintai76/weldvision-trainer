# WeldVision Trainer 2.0: Hybrid Platform Architecture

This document defines the structural division of labor, network payload contracts, and integration flywheels between the **Android Trainer App (Trainee)** and the **Web Dashboard (Instructor)**.

---

## 1. System Topology Overview

WeldVision Trainer 2.0 implements a **Hybrid Edge-Cloud Architecture**. Real-time physical metrics calculation and AR rendering occur on the edge (Android device) to guarantee zero-latency feedback ($< 16.6\text{ ms}$ at $60\text{ Hz}$). Heavy analytical aggregation, class grading, and historical 3D path reconstruction are handled in the cloud (Cloudflare Workers) and rendered on the client browser (WebGL/Three.js).

```
 +--------------------------------------------------------------------------------+
 |                           ANDROID CLIENT (ON-DEVICE)                           |
 |                                                                                |
 |  [Sensors] --> (Complementary Fusion Filter) --> [Dynamic Scoring Engine]     |
 |                                                           |                    |
 |  [CameraX] --> (AprilTag PnP Tracking)       --> [Local Room Cache]            |
 |                                                           |                    |
 |  [Auditory/Haptic Cues] <-- (Instant Feedback) <----------+                    |
 +-----------------------------------------------------------|--------------------+
                                                             | (Async gzip upload)
                                                             v
 +--------------------------------------------------------------------------------+
 |                        CLOUDFLARE BACKEND SYSTEM (EDGE)                        |
 |                                                                                |
 |                     +----------------------------------+                       |
 |                     |   Edge Worker JSON Ingestion     |                       |
 |                     +----------------+-----------------+                       |
 |                                      |                                         |
 |                     +----------------+-----------------+                       |
 |                     |                                  |                       |
 |                     v                                  v                       |
 |              [Cloudflare D1]                    [Cloudflare R2]                |
 |            Relational Summary                Time-Series Coordinate            |
 |              Database (SQL)                    Object Store (gzip)             |
 +-------------------------------------------------------|------------------------+
                                                         | (On-demand WebGL Query)
                                                         v
 +--------------------------------------------------------------------------------+
 |                           INSTRUCTOR WEB PORTAL                                |
 |                                                                                |
 |  - Class Telemetry Graphs & Grade Books                                        |
 |  - Three.js 3D Coordinate Replay (Reconstructs p_W_tip(t))                     |
 |  - WeldVision X5 NDT Metallurgical Alignment View                              |
 +--------------------------------------------------------------------------------+
```

---

## 1.5 Physical Setup: The Fixture Frame

A critical design distinction in the physical setup is that the AprilTags are **NOT** placed on the consumable steel coupons. Placing tags on the workpiece would result in tags being damaged by heat, spatter, and grinding, adding recurring setup costs.

Instead, the AprilTags are permanently mounted on a **rigid fixture frame** that holds the workpiece.
- The frame is fixed to the welding table and has 2–4 AprilTags at known positions defining the World origin $[W]$.
- The frame holds standard bare steel coupons.
- Because the tags on the fixture do not move, the app knows the exact position of the coupon relative to the tags as soon as the student inserts a new one, requiring zero setup per session.

---

## 2. Dynamic Performance Space Math

The physical-digital alignment must be consistently maintained between both platforms. The Android app logs the raw telemetry, which is later reconstructed on the instructor's web canvas.

- **Tip Translation Coordinate Stream:** Represented as $\vec{p}_{W,\text{tip}}(t) = [x_t, y_t, z_t]^T \in \mathbb{R}^3$ relative to the primary AprilTag origin $[W]$.
- **Torch Heading Vector:** Reconstructed from the fused orientation quaternion $\mathbf{q}_{\text{fused}}(t)$ to verify travel angle $\theta_{\text{travel}}(t)$ and work angle $\phi_{\text{work}}(t)$:

$$\theta_{\text{travel}}(t) = \arcsin\left( \vec{v}_T(t) \cdot \vec{d}_W \right)$$

$$\phi_{\text{work}}(t) = \arccos\left( \vec{v}_T(t) \cdot \vec{n}_W \right)$$

Where:
- $\vec{d}_W$ is the standardized horizontal joint seam unit vector.
- $\vec{n}_W$ is the surface normal of the horizontal base plate.

### Pose Outlier Filtering Constraints

To prevent camera noise or reflections from introducing false trajectories into the 3D replay stream, the on-device tracker applies a strict spatial boundary filter:

$$\Vert{} \vec{p}_{W,\text{tip}}(t) \Vert{} \le 500.0\text{ mm}$$

Any tracking coordinate frame that exceeds this absolute radial envelope is flagged as a visual outlier and dropped before sensor fusion integration.

---

## 3. Storage & Ingestion Division Strategy

To prevent relational database bloating and eliminate expensive network egress fees, data is split into **Summary Data** and **Detail Streams**.

### A. Summary Payload (Indexed in Cloudflare D1 SQL)

Stores metadata, student identification metrics, assignment profiles, and final evaluation scores. This keeps dashboard loads extremely fast.

```sql
CREATE TABLE IF NOT EXISTS PracticeSessions (
    session_id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL,
    assignment_id VARCHAR(64) NOT NULL,
    overall_score REAL NOT NULL,          -- Grading performance percentage
    avg_arc_length REAL NOT NULL,          -- Average distance in mm
    avg_travel_speed REAL NOT NULL,        -- Average speed in mm/s
    avg_work_angle REAL NOT NULL,          -- Average angle in degrees
    avg_travel_angle REAL NOT NULL,        -- Average angle in degrees
    telemetry_object_key VARCHAR(256) NOT NULL, -- Direct path pointer to R2 bucket
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Optimization indices to prevent layout lag on the Instructor dashboard
CREATE INDEX IF NOT EXISTS idx_sessions_performance
    ON PracticeSessions(student_id, overall_score DESC);

CREATE INDEX IF NOT EXISTS idx_sessions_assignment
    ON PracticeSessions(assignment_id, created_at DESC);
```

### B. Detail Stream Payload (Archived in Cloudflare R2 Object Storage)

Stores the raw, high-frequency coordinate frames ($60\text{ Hz}$) packaged as compressed, gzip-formatted JSON files. These are loaded **on-demand** only when the instructor initiates a 3D replay session.

- **R2 Object Key Formatting Pattern:**

```
/artifacts/{appId}/users/{studentId}/sessions/{sessionId}_telemetry.json.gz
```

- **Frame Structure:**

```json
[
  {"t": 0.00, "x": 0.0, "y": 0.0, "z": 3.0, "travel_angle": 12.1, "work_angle": 89.1},
  {"t": 0.02, "x": 0.1, "y": 0.0, "z": 3.1, "travel_angle": 12.2, "work_angle": 89.0}
]
```

---

## 4. The Instructor Web Replay Suite (Three.js Engine)

When an instructor loads a student's session:

1. The Web Dashboard queries D1 to retrieve the session's metadata and R2 pointer.
2. The browser streams and decompresses the `telemetry.json.gz` file directly from R2.
3. A **Three.js (WebGL)** renderer constructs a virtual mock-up of the T-joint plate workspace and draws a dynamic 3D ribbon path matching $\vec{p}_{W,\text{tip}}(t)$:

```
           [Instructor Web UI Replay Screen]
   +-----------------------------------------------+
   | Play [ > ]  Pause [ || ]  Speed [ 1.0x ]      |
   |                                               |
   |           /| (Vertical Plate)                 |
   |          / |                                  |
   |         /  |   [3D Telemetry Path Ribbon]     |
   |        /   |  =======================>        |
   |       /____|________________________          |
   |      (Horizontal Base Plate)                  |
   +-----------------------------------------------+
```

### Browser-Side Implementation

The web dashboard uses the browser-native `DecompressionStream("gzip")` API to stream-decompress R2 archives with zero external dependencies. An async generator yields frames incrementally, feeding the Three.js render loop without buffering all 100k+ frames in memory. See `web-dashboard/telemetry-stream.ts` for the full implementation.

---

## 5. WeldVision X5 Physical Inspection Closed-Loop Integration

This is the platform's ultimate competitive differentiator. Because both the **Trainer App** and the **WeldVision X5 Inspection camera** share the standardized World coordinate space $[W]$ defined by the physical AprilTag marker arrays, their datasets can be directly overlaid on the Web Dashboard.

```
                  Weld Seam Spatial Axis (mm)
   0.00        50.00       100.00       150.00       200.00
   +------------+------------+------------+------------+--->
   |///////////////////////////////////////////////////|  <-- Trainer 2.0 AR Speed (mm/s)
   |            ^ [WARNING: Speed Spiked to 6.2mm/s]   |
   +------------+------------+------------+------------+
   |===================================================|  <-- WeldVision X5 NDT Defect Map
   |            * [DEFECT LOCATED: Undercut detected]  |
   +------------+------------+------------+------------+
```

### The Analytical Loop

1. During training, the student's speed spike is recorded at coordinate $y = 52.4\text{ mm}$.
2. During physical evaluation of the real weld plate, the WeldVision X5 camera runs non-destructive testing (NDT) computer vision algorithms on the metal joint.
3. The camera flags an **undercut defect** at position $y = 52.1\text{ mm}$.
4. The Web Portal merges these coordinates, demonstrating to both student and instructor the direct physical correlation between incorrect manual technique and actual weld failure.

---

## 6. Project Structure

```
weldvision-trainer/
├── app/                          # Android Trainer App (Trainee)
│   └── src/main/java/com/example/
│       ├── MainActivity.kt
│       ├── data/                 # Room DB, Retrofit sync, entities
│       └── ui/                   # Compose UI, CameraX, theme
├── web-dashboard/                # Instructor Web Portal
│   ├── telemetry-stream.ts       # R2 gzip parser + Three.js helpers
│   └── replay-viewer.html        # Full Three.js 3D replay demo
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

---

## 7. Mathematical Safety Bounds Summary

| Constraint | Formula | Purpose |
|---|---|---|
| Tip position envelope | $\Vert \vec{p}_{W,\text{tip}}(t) \Vert \le 500.0\text{ mm}$ | Drop tracking anomalies at edge |
| Travel angle | $\theta_{\text{travel}}(t) = \arcsin(\vec{v}_T \cdot \vec{d}_W)$ | Verify torch heading vs seam |
| Work angle | $\phi_{\text{work}}(t) = \arccos(\vec{v}_T \cdot \vec{n}_W)$ | Verify torch tilt vs base plate |
| AR feedback latency | $\lt 16.6\text{ ms}$ | Guarantee 60 Hz frame budget |
| Telemetry sample rate | $60\text{ Hz}$ | Match CameraX frame cadence |
