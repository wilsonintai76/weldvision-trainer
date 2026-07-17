```text
====================================== APPLICATION & DEVICE LAYER ======================================
  [ Android: android-app/ ]                               [ Web Browser: webvision-studio/ ]
   • Camera AprilTag Computer Vision                        • Three.js WebGL Core Engine
   • C++ Calibration Solver (geometry/calibration)          • React 19 + Vite + Tailwind CSS
   • Multi-User MQTT Publisher                              • Classroom Command Center PWA
   • Local SQLite Room Database                             • Focused Viewport Pattern
                    │                                                        │
                    │ weldvision/student/{seat}/live                         │ weldvision/student/+/live
                    └─────────────────────────┐    ┌─────────────────────────┘
                                              ▼    ▼
====================================== NETWORKING & BROKER LAYER ======================================
  [ telemetry-server/ — Aedes MQTT Broker ]
   • Embedded Node.js MQTT Broker (zero external deps)
   • TCP :1883 ← Android phones
   • WebSocket :9001 ← Browser PWA
   • Wildcard topic routing: weldvision/student/+/live
   • Replaces external Mosquitto installation
                    │
                    │ (REST API)
                    ▼
====================================== CLOUD EDGE LAYER ===============================================
  [ webvision-studio/src/workers/ — Cloudflare Worker ]
   • Hono 4 Framework + Zod Validation
   • Cloudflare D1 Database (sessions, students, scores)
   • Cloudflare R2 Bucket (60 Hz telemetry archives)
   • Cloudflare KV (real-time roster cache)
   • Cloudflare Workers AI (defect detection via Llama 3.3)
                    │
                    ▼
====================================== STORAGE & PERSISTENCE LAYER =====================================
  [ Cloud Relational ]                  [ Cloud Object Archive ]         [ Cloud Cache ]
   • D1: Tabular accounts,              • R2: Coordinate streams         • KV: Roster state
     rosters, scores                      (gzip JSON, 60 Hz)              (5-min TTL heartbeat)
```

## Repository Structure

```
weldvision-trainer/
├── android-app/              # Student Mobile Tracker (Gradle/CMake)
│   ├── app/
│   │   └── src/main/cpp/     # C++ Geometry & Calibration Solvers
│   └── src/main/java/        # Kotlin Mobile Core & Pipeline
│
├── webvision-studio/         # Instructor UI Dashboard (React/TypeScript)
│   ├── src/
│   │   ├── components/       # Three.js Canvas + Roster UI
│   │   ├── hooks/            # MQTT + 3D Renderer hooks
│   │   ├── lib/              # Types, MQTT client, Hono RPC client
│   │   └── workers/          # Cloudflare Worker (Hono API)
│   └── public/               # PWA manifest + service worker
│
├── telemetry-server/         # Live Network Bridge (Node.js/Aedes)
│   └── server.ts             # MQTT broker + WebSocket relay
│
├── core-assets/              # Shared 3D Models & Print Materials
│   ├── models/               # t_joint_coupon.glb, mig_nozzle.glb
│   └── prints/               # apriltag_6x4_target.svg
│
├── ARCHITECTURE.md
└── README.md
```
