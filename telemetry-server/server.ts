import { WebSocketServer, WebSocket } from "ws";
import { createServer, IncomingMessage } from "http";

// ── Types ────────────────────────────────────────────

interface ClientInfo {
  bracketId: string;
  studentId: string;
  role: "student" | "instructor";
  ws: WebSocket;
  connectedAt: number;
}

interface SessionInfo {
  sessionId: string;
  bracketId: string;
  studentId: string;
  startedAt: number;
  endedAt?: number;
  totalTravelMm?: number;
  durationSec?: number;
}

// ── State ────────────────────────────────────────────

const PORT = parseInt(process.env.PORT || "8080", 10);

const clients = new Map<WebSocket, ClientInfo>();
const instructorSockets = new Set<WebSocket>();
const activeSessions = new Map<string, SessionInfo>();

// ── Logging ──────────────────────────────────────────

function log(level: "info" | "warn" | "error", message: string, data?: unknown) {
  const timestamp = new Date().toISOString();
  const prefix = `[${timestamp}] [${level.toUpperCase()}]`;
  if (data != null) {
    console[level](`${prefix} ${message}`, JSON.stringify(data, null, 2));
  } else {
    console[level](`${prefix} ${message}`);
  }
}

// ── Broadcast helpers ────────────────────────────────

function broadcastToInstructors(message: object): void {
  const payload = JSON.stringify(message);
  let sent = 0;
  for (const ws of instructorSockets) {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(payload);
      sent++;
    }
  }
  if (sent > 0) {
    log("info", `Broadcast to ${sent} instructor(s)`);
  }
}

function sendToClient(ws: WebSocket, message: object): void {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

// ── Find student by bracket/student ID ───────────────

function findStudentSocket(bracketId: string, studentId: string): WebSocket | null {
  for (const [ws, info] of clients) {
    if (
      info.role === "student" &&
      info.bracketId === bracketId &&
      info.studentId === studentId
    ) {
      return ws;
    }
  }
  return null;
}

// ── HTTP server (health check) ───────────────────────

const httpServer = createServer((_req: IncomingMessage, res) => {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      service: "WeldVision Telemetry Server",
      status: "running",
      clients: clients.size,
      instructors: instructorSockets.size,
      activeSessions: activeSessions.size,
      uptime: process.uptime(),
    })
  );
});

// ── WebSocket server ─────────────────────────────────

const wss = new WebSocketServer({ server: httpServer });

wss.on("connection", (ws: WebSocket) => {
  const connectedAt = Date.now();
  log("info", "New connection");

  ws.on("message", (raw) => {
    let msg: Record<string, unknown>;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      log("warn", "Invalid JSON received");
      sendToClient(ws, { type: "error", message: "Invalid JSON" });
      return;
    }

    switch (msg.type) {
      // ── Registration ───────────────────────────────

      case "register": {
        const info: ClientInfo = {
          bracketId: msg.bracketId as string,
          studentId: msg.studentId as string,
          role: msg.role as "student" | "instructor",
          ws,
          connectedAt,
        };
        clients.set(ws, info);

        if (info.role === "instructor") {
          instructorSockets.add(ws);
        }

        log("info", `Registered: ${info.role}`, {
          bracketId: info.bracketId,
          studentId: info.studentId,
        });

        sendToClient(ws, {
          type: "registered",
          bracketId: info.bracketId,
          studentId: info.studentId,
          role: info.role,
        });
        break;
      }

      // ── Telemetry frame ────────────────────────────

      case "telemetry_frame": {
        broadcastToInstructors(msg);
        break;
      }

      // ── Calibration profile ────────────────────────

      case "calibration_profile": {
        const bracketId = msg.bracketId as string;
        const studentId = msg.studentId as string;
        const profile = msg.profile as Record<string, unknown> | undefined;
        const tcpQuality = (profile as Record<string, unknown>)?.tcp as Record<string, unknown> | undefined;
        const quality = tcpQuality?.quality as Record<string, unknown> | undefined;

        log("info", `Calibration profile: ${bracketId}/${studentId}`, {
          coverage: quality?.coveragePercent,
          condition: quality?.conditionNumber,
          residual: quality?.residualMm,
          workpieceMethod: (profile as Record<string, unknown>)?.workpiece as Record<string, unknown> | undefined,
        });

        broadcastToInstructors(msg);
        break;
      }

      // ── Session start ──────────────────────────────

      case "session_start": {
        const session: SessionInfo = {
          sessionId: msg.sessionId as string,
          bracketId: msg.bracketId as string,
          studentId: msg.studentId as string,
          startedAt: Date.now(),
        };
        activeSessions.set(session.sessionId, session);
        log("info", `Session started: ${session.sessionId}`);
        broadcastToInstructors(msg);
        break;
      }

      // ── Session end ────────────────────────────────

      case "session_end": {
        const sessionId = msg.sessionId as string;
        const session = activeSessions.get(sessionId);
        if (session) {
          session.endedAt = Date.now();
          session.totalTravelMm = msg.totalTravelMm as number;
          session.durationSec = msg.durationSec as number;
        }
        log("info", `Session ended: ${sessionId}`, {
          travelMm: msg.totalTravelMm,
          durationSec: msg.durationSec,
        });
        broadcastToInstructors(msg);
        break;
      }

      // ── Recalibration request (instructor → student) ──

      case "recalibration_request": {
        const bracketId = msg.bracketId as string;
        const studentId = msg.studentId as string;
        const reason = msg.reason as string;

        log("info", `Recalibration requested: ${bracketId}/${studentId}`, { reason });

        const studentWs = findStudentSocket(bracketId, studentId);
        if (studentWs) {
          sendToClient(studentWs, {
            type: "recalibration_request",
            reason,
            timestampMs: Date.now(),
          });
          log("info", "Recalibration request delivered to student");
        } else {
          log("warn", "Student not connected — recalibration request not delivered");
        }
        break;
      }

      // ── Unknown ────────────────────────────────────

      default: {
        log("warn", `Unknown message type: ${msg.type}`);
        sendToClient(ws, {
          type: "error",
          message: `Unknown message type: ${msg.type}`,
        });
      }
    }
  });

  // ── Disconnect ─────────────────────────────────────

  ws.on("close", (code: number, reason: Buffer) => {
    const info = clients.get(ws);
    if (info) {
      log("info", `Disconnected: ${info.role}`, {
        bracketId: info.bracketId,
        studentId: info.studentId,
        code,
        reason: reason.toString(),
        durationSec: ((Date.now() - info.connectedAt) / 1000).toFixed(1),
      });

      if (info.role === "instructor") {
        instructorSockets.delete(ws);
      }
    }
    clients.delete(ws);
  });

  ws.on("error", (err: Error) => {
    log("error", "WebSocket error", { message: err.message });
  });
});

// ── Start ────────────────────────────────────────────

httpServer.listen(PORT, () => {
  log("info", `WeldVision Telemetry Server running on ws://localhost:${PORT}`);
  log("info", `Health check: http://localhost:${PORT}`);
});

// ── Graceful shutdown ────────────────────────────────

process.on("SIGINT", () => {
  log("info", "Shutting down...");
  wss.close(() => {
    httpServer.close(() => {
      process.exit(0);
    });
  });
});
