```text
====================================== APPLICATION & DEVICE LAYER ======================================
  [ Android: WeldVision Trainer App ]                      [ Web Browser: WeldSim Studio App ]
   • Camera AprilTag Computer Vision                        • Three.js WebGL Core Engine
   • Android Biometric Enclave API                          • SQLite-WASM Runtime Thread
   • Local SQLite Room Database                             • OPFS Storage (*Requires COOP/COEP)
                    │                                                        │
                    │ (Secure Registration / Sync)                           │ (Fetch Roster / UI Sync)
                    └─────────────────────────┐    ┌─────────────────────────┘
                                              ▼    ▼
====================================== NETWORKING & BROKER LAYER ======================================
  [ INBOUND TRANSFERS ] ──────────────────────────────────────────────► [ BACKEND WEB ROUTING ]
   • Live Telemetry Pipeline (Online/Classroom Mode)                      • REST API Orchestration
      ├──► PRIMARY LOCAL LANE ──► Local Mosquitto LAN [TCP:1883]          • Stateless Web Endpoints
      └──► WAN FALLBACK LANE  ──► Cloud HiveMQ Cluster [TLS:8883]
   • Offline Queue Pipeline (Home Mode)
      └──► ASYNC CACHING INTERFACE ──► Local JSON Logs + Room DB Sync
                    │                                                        │
                    │ (Live Streams via WebSockets 9001 / 8884)               │ (HTTPS Multipart Uploads)
                    ▼                                                        ▼
====================================== COMPUTE & INGESTION LAYER ======================================
  [ Client-Side Browser Workspace ]                       [ Serverless Cloudflare Edge Layer ]
   • Reads Local SQLite-WASM/OPFS Data                     • Cloudflare Worker Core Router
   • Drives Real-time 3D Bead Growth                       • WeldSim Thermophysics Engine
   • Computes Local UI Math Matrices                       • Cloudflare Workers AI Framework
                    │                                                        │
                    │ (Lecturer Grade Commits)                               │ (Persistent Writes)
                    └─────────────────────────┐    ┌─────────────────────────┘
                                              ▼    ▼
====================================== STORAGE & PERSISTENCE LAYER =====================================
  [ Cloud Relational Data Storage ]                       [ Cloud Object Data Archive ]
   • Cloudflare D1 Database                                • Cloudflare R2 Bucket Store
   • Stores Tabular Accounts, Rosters & Scores             • Archives 60Hz Coordinate Streams (.json)
```
