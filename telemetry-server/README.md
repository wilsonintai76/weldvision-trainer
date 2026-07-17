# WeldVision Telemetry Bridge Server

Embedded MQTT broker + WebSocket relay for the WeldVision classroom.
Runs on the instructor's laptop. Replaces the need for a separate
Mosquitto installation.

## Quick Start

```bash
cd telemetry-server
npm install
npm run dev
```

## Architecture

```
Android Phone 1 ──► MQTT TCP :1883 ──┐
Android Phone 2 ──► MQTT TCP :1883 ──┼──► Aedes Broker
Android Phone 3 ──► MQTT TCP :1883 ──┘       │
                                              │
WebVision Studio ◄── WebSocket :9001 ◄────────┘
```

## Ports

| Port | Protocol | Client |
|------|----------|--------|
| 1883 | MQTT TCP | Android phones |
| 9001 | WebSocket | Browser PWA dashboard |

## Topic Wildcard

```
weldvision/student/+/live    → All live telemetry frames
weldvision/student/+/status  → Student status heartbeats
```

## Dependencies

- [Aedes](https://github.com/moscajs/aedes) — Zero-dependency MQTT broker for Node.js
- [ws](https://github.com/websockets/ws) — WebSocket server for browser clients
