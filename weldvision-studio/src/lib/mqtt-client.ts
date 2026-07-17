/**
 * WeldVision Trainer 2.0 — MQTT-over-WebSocket Client
 * ====================================================
 *
 * Connects to the instructor's local Mosquitto broker via WebSocket (port 9001)
 * and subscribes to wildcard topic: weldvision/student/+/live
 *
 * This is the React-compatible version of the MQTT client, emitting roster
 * updates via callbacks that the useClassroomTelemetry hook consumes.
 */

import mqtt from "mqtt";
import type {
  StudentTelemetryFrame,
  ClassroomRoster,
  RosterEntry,
} from "./types";

const MAX_HISTORY = 500;
const IDLE_TIMEOUT_MS = 2000;

export type RosterChangeCallback = (roster: ClassroomRoster) => void;
export type FrameCallback = (topic: string, frame: StudentTelemetryFrame) => void;

export class ClassroomMqttClient {
  brokerHost: string;
  private wsPort: number;
  private client: mqtt.MqttClient | null = null;
  private statusMonitor: ReturnType<typeof setInterval> | null = null;
  private _roster: ClassroomRoster = {};
  private _connected = false;

  onRosterChange: RosterChangeCallback | null = null;
  onFrame: FrameCallback | null = null;
  onConnectionChange: ((connected: boolean, error?: string) => void) | null = null;

  constructor(brokerHost = "localhost", wsPort = 9001) {
    this.brokerHost = brokerHost;
    this.wsPort = wsPort;
  }

  get roster(): ClassroomRoster {
    return this._roster;
  }
  get connected(): boolean {
    return this._connected;
  }
  getActiveCount(): number {
    return Object.values(this._roster).filter((r) => r.isWelding).length;
  }
  getRosterList(): RosterEntry[] {
    return Object.values(this._roster).sort((a, b) => a.seat.localeCompare(b.seat));
  }

  connect(): void {
    if (this.client) return;
    const wsUrl = `ws://${this.brokerHost}:${this.wsPort}`;

    this.client = mqtt.connect(wsUrl, {
      clientId: `weldvision_dash_${Math.random().toString(36).slice(2, 8)}`,
      clean: true,
      reconnectPeriod: 2000,
      connectTimeout: 5000,
    });

    this.client.on("connect", () => {
      this._connected = true;
      this.client?.subscribe("weldvision/student/+/live", { qos: 0 });
      this.client?.subscribe("weldvision/student/+/status", { qos: 0 });
      this.onConnectionChange?.(true);
      this.startStatusMonitor();
    });

    this.client.on("message", (_topic: string, payload: Buffer) => {
      try {
        const data = JSON.parse(payload.toString()) as StudentTelemetryFrame;
        this.upsertRoster(data);
        this.onFrame?.(_topic, data);
      } catch { /* skip corrupted frames */ }
    });

    this.client.on("error", (err: Error) => {
      this.onConnectionChange?.(false, err.message);
    });

    this.client.on("close", () => {
      this._connected = false;
      this.onConnectionChange?.(false);
      this.stopStatusMonitor();
    });
  }

  disconnect(): void {
    this.stopStatusMonitor();
    this.client?.end();
    this.client = null;
    this._connected = false;
  }

  private upsertRoster(frame: StudentTelemetryFrame): void {
    const seat = frame.meta.seat;
    const existing = this._roster[seat];
    const history = existing ? [...existing.frameHistory, frame] : [frame];
    if (history.length > MAX_HISTORY) history.splice(0, history.length - MAX_HISTORY);

    this._roster = {
      ...this._roster,
      [seat]: {
        name: frame.meta.name,
        seat,
        studentId: frame.meta.studentId,
        isWelding: true,
        score: existing?.score ?? 0,
        defect: existing?.defect ?? "",
        latestFrame: frame,
        frameHistory: history,
      },
    };
  }

  private startStatusMonitor(): void {
    this.stopStatusMonitor();
    this.statusMonitor = setInterval(() => {
      const now = Date.now();
      let changed = false;
      const updated = { ...this._roster };
      for (const seat of Object.keys(updated)) {
        const entry = updated[seat];
        if (now - (entry.latestFrame?.timestamp ?? 0) > IDLE_TIMEOUT_MS) {
          if (entry.isWelding) {
            updated[seat] = { ...entry, isWelding: false };
            changed = true;
          }
        }
      }
      if (changed) {
        this._roster = updated;
        this.onRosterChange?.(this._roster);
      }
    }, 2000);
  }

  private stopStatusMonitor(): void {
    if (this.statusMonitor) {
      clearInterval(this.statusMonitor);
      this.statusMonitor = null;
    }
  }
}
