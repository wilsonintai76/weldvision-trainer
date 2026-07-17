<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# WeldVision Trainer 2.0

WeldVision Trainer 2.0 is a **Hybrid Edge-Cloud Welding Simulation Platform** with multi-user classroom MQTT telemetry, C++ SVD calibration solvers, and AI-powered defect detection.

## Repository Structure

| Directory | Role | Tech |
|-----------|------|------|
| [`android-app/`](android-app/) | Student mobile tracker | Kotlin, C++ (AprilTag, SVD geometry), Room DB |
| [`webvision-studio/`](webvision-studio/) | Instructor dashboard PWA | React 19, Three.js, Tailwind CSS, Hono Worker |
| [`telemetry-server/`](telemetry-server/) | Live MQTT bridge | Node.js, Aedes broker |
| [`core-assets/`](core-assets/) | Shared 3D models & prints | glTF 2.0, SVG |

## Architecture

```
Android Phones ──► MQTT (TCP :1883) ──► telemetry-server/ ──► WebSocket (:9001) ──► webvision-studio/
                                             │
                                             ▼
                                     Cloudflare Worker
                                     (D1 + R2 + KV + Workers AI)
```

## Quick Start

### 1. Telemetry Bridge (instructor laptop)

```bash
cd telemetry-server
npm install && npm run dev
```

### 2. WebVision Studio (instructor browser)

```bash
cd webvision-studio
npm install && npm run dev
# → http://localhost:5173
```

### 3. Android App (student phones)

```bash
cd android-app
./gradlew assembleDebug
```

## Key Features

- **60 Hz C++ Solver** — On-device AprilTag tracking + SVD-based TCP calibration with condition number gating
- **Multi-User Classroom MQTT** — Dynamic topic routing `weldvision/student/{seat}/live` with wildcard subscription
- **Focused Viewport Pattern** — Single WebGL context renders selected student's weld in high-fidelity 3D
- **AI Defect Detection** — Cloudflare Workers AI (Llama 3.3 70B) analyzes telemetry for porosity, undercut, and spatter
- **PWA Offline Support** — Service worker with cache-first strategy for classroom reliability

## License

Proprietary — WeldVision Trainer 2.0
