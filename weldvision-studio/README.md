# WeldVision Trainer 2.0 — Classroom Command Center

Multi-user GMAW welding simulator dashboard with real-time 3D telemetry,
AI-powered defect detection, and Cloudflare edge infrastructure.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19 + Vite 6 + Tailwind CSS 4 + TypeScript |
| 3D Rendering | Three.js 0.170 |
| Real-time | MQTT.js 5 (WebSocket to local Mosquitto broker) |
| API Backend | Hono 4 (Cloudflare Worker) |
| Database | Cloudflare D1 (SQLite) |
| Storage | Cloudflare R2 (telemetry archives) |
| Cache | Cloudflare KV (roster state) |
| AI | Cloudflare Workers AI (defect detection) |

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   INSTRUCTOR LAPTOP                       │
│  ┌─────────────────────┐  ┌────────────────────────────┐ │
│  │ Mosquitto Broker     │  │ PWA Classroom Dashboard    │ │
│  │ TCP:1883  WS:9001   │◄─│ React + Three.js           │ │
│  └──▲────────▲─────────┘  └────────┬───────────────────┘ │
│     │        │                      │ HTTP                 │
│  ┌──┴──┐ ┌──┴──┐           ┌───────┴────────┐           │
│  │Phone│ │Phone│           │ Cloudflare Edge │           │
│  │  1  │ │  2  │           │ Hono Worker API │           │
│  └─────┘ └─────┘           │ D1 + R2 + KV + AI│          │
│                             └────────────────┘           │
└──────────────────────────────────────────────────────────┘
```

## Getting Started

### Prerequisites

- Node.js 20+
- Mosquitto MQTT broker ([download](https://mosquitto.org/download/))
- Cloudflare account (for D1/R2/KV/AI)

### 1. Install dependencies

```bash
cd web-dashboard
npm install
```

### 2. Start local Mosquitto broker

```bash
# Copy the config from legacy/
cp legacy/mosquitto.conf /path/to/mosquitto/
mosquitto -c mosquitto.conf -v
```

### 3. Start the React dev server

```bash
npm run dev
# → http://localhost:5173
```

### 4. Start the Hono Worker (local)

```bash
npm run worker:dev
# → http://localhost:8787
```

### 5. Initialize D1 database

```bash
npm run db:local
```

### 6. Deploy to Cloudflare

```bash
npm run build
npm run worker:deploy
```

## Project Structure

```
web-dashboard/
├── public/                    # Static assets
│   ├── manifest.json          # PWA manifest
│   └── sw.js                  # Service worker
├── src/
│   ├── App.tsx                # Root component
│   ├── main.tsx               # Entry point
│   ├── index.css              # Tailwind + custom theme
│   ├── components/
│   │   ├── ClassroomDashboard.tsx  # Main layout
│   │   ├── RosterSidebar.tsx       # Student list
│   │   ├── StudentCard.tsx         # Individual student KPI
│   │   ├── WeldViewport.tsx        # 3D Three.js canvas
│   │   └── BrokerConnector.tsx     # MQTT connection UI
│   ├── hooks/
│   │   ├── useClassroomTelemetry.ts # MQTT roster hook
│   │   └── useThreeRenderer.ts     # Three.js renderer hook
│   ├── lib/
│   │   ├── types.ts                # TypeScript types
│   │   ├── mqtt-client.ts          # MQTT WebSocket client
│   │   └── hono-client.ts          # Hono RPC client
│   └── workers/
│       ├── index.ts                # Hono app entry
│       ├── api/
│       │   ├── sessions.ts         # D1 session CRUD
│       │   ├── telemetry.ts        # R2 + Workers AI
│       │   └── roster.ts           # KV roster cache
│       └── db/
│           └── schema.sql          # D1 schema
├── legacy/                   # Previous standalone HTML
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── wrangler.toml
└── README.md
```

## MQTT Topic Architecture

```
[Student Phones]                 [Dashboard]
      │                              │
      │ weldvision/student/seat_01/live
      ├──────────────────────────────┤
      │ weldvision/student/seat_02/live
      ├──────────────────────────────┤
      │ weldvision/student/+/live    │  ← Wildcard subscription
      │                              │
      ▼                              ▼
  Mosquitto Broker              ClassroomMqttClient
  (TCP 1883 / WS 9001)          (WebSocket)
```

## API Endpoints (Hono Worker)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/roster` | Active classroom roster (KV) |
| PUT | `/api/roster/:seat` | Update student status |
| GET | `/api/sessions` | List sessions (D1) |
| GET | `/api/sessions/:id` | Session detail |
| POST | `/api/sessions` | Create session |
| PUT | `/api/sessions/:id/score` | Update scores |
| GET | `/api/telemetry/:key` | Download archive (R2) |
| POST | `/api/telemetry/upload` | Upload archive |
| POST | `/api/telemetry/analyze` | AI defect analysis |

## License

Proprietary — WeldVision Trainer 2.0
