/**
 * useClassroomTelemetry — React hook wrapping ClassroomMqttClient.
 *
 * Manages connection lifecycle, roster state, and per-student frame routing.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { ClassroomMqttClient } from "../lib/mqtt-client";
import type {
  ClassroomRoster,
  StudentTelemetryFrame,
  RosterEntry,
} from "../lib/types";

interface UseClassroomTelemetryReturn {
  roster: ClassroomRoster;
  rosterList: RosterEntry[];
  connected: boolean;
  activeCount: number;
  brokerHost: string;
  setBrokerHost: (host: string) => void;
  connect: () => void;
  disconnect: () => void;
  selectedSeat: string | null;
  setSelectedSeat: (seat: string | null) => void;
  selectedHistory: StudentTelemetryFrame[];
  onFrame: ((frame: StudentTelemetryFrame) => void) | null;
  setOnFrame: (cb: ((frame: StudentTelemetryFrame) => void) | null) => void;
}

export function useClassroomTelemetry(
  initialHost = "localhost"
): UseClassroomTelemetryReturn {
  const clientRef = useRef<ClassroomMqttClient | null>(null);
  const [roster, setRoster] = useState<ClassroomRoster>({});
  const [connected, setConnected] = useState(false);
  const [brokerHost, setBrokerHostState] = useState(initialHost);
  const [selectedSeat, setSelectedSeat] = useState<string | null>(null);
  const onFrameRef = useRef<((frame: StudentTelemetryFrame) => void) | null>(null);

  // ── Connect / Disconnect ──────────────────────────────────────────────

  const connect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.disconnect();
    }
    const client = new ClassroomMqttClient(brokerHost, 9001);
    clientRef.current = client;

    client.onConnectionChange = (conn, err) => setConnected(conn);

    client.onRosterChange = (newRoster) => {
      setRoster({ ...newRoster });
    };

    client.onFrame = (_topic, frame) => {
      // Push to the 3D renderer callback if set
      onFrameRef.current?.(frame);
    };

    client.connect();
  }, [brokerHost]);

  const disconnect = useCallback(() => {
    clientRef.current?.disconnect();
    clientRef.current = null;
    setConnected(false);
  }, []);

  const setBrokerHost = useCallback((host: string) => {
    setBrokerHostState(host);
  }, []);

  const setOnFrame = useCallback(
    (cb: ((frame: StudentTelemetryFrame) => void) | null) => {
      onFrameRef.current = cb;
    },
    []
  );

  // ── Lifecycle ─────────────────────────────────────────────────────────

  useEffect(() => {
    return () => {
      clientRef.current?.disconnect();
    };
  }, []);

  // ── Derived ───────────────────────────────────────────────────────────

  const rosterList = clientRef.current?.getRosterList() ?? [];

  const activeCount = clientRef.current?.getActiveCount() ?? 0;

  const selectedHistory: StudentTelemetryFrame[] = selectedSeat
    ? roster[selectedSeat]?.frameHistory ?? []
    : [];

  return {
    roster,
    rosterList,
    connected,
    activeCount,
    brokerHost,
    setBrokerHost,
    connect,
    disconnect,
    selectedSeat,
    setSelectedSeat,
    selectedHistory,
    onFrame: onFrameRef.current,
    setOnFrame,
  };
}
