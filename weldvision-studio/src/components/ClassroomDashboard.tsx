/**
 * ClassroomDashboard — Top-level layout composing the roster sidebar,
 * focused 3D weld viewport, and post-weld analysis panel.
 */

import { useState, useMemo, useEffect } from "react";
import { useClassroomTelemetry } from "../hooks/useClassroomTelemetry";
import { useThreeRenderer } from "../hooks/useThreeRenderer";
import { RosterSidebar } from "./RosterSidebar";
import { WeldViewport } from "./WeldViewport";
import { BrokerConnector } from "./BrokerConnector";
import WeldAnalysis from "./WeldAnalysis";
import { analyzeWeld } from "../lib/analysis-engine";

type ActiveTab = "3d" | "analysis";

export function ClassroomDashboard() {
  const telemetry = useClassroomTelemetry("localhost");
  const renderer = useThreeRenderer();
  const [activeTab, setActiveTab] = useState<ActiveTab>("3d");

  // Route incoming MQTT frames to the 3D renderer when the selected
  // student's seat matches.
  useEffect(() => {
    telemetry.setOnFrame((frame) => {
      if (frame.meta.seat === telemetry.selectedSeat) {
        const existing = telemetry.selectedHistory;
        renderer.loadHistory([...existing, frame]);
      }
    });
  }, [telemetry.selectedSeat, telemetry.selectedHistory.length]);

  // When selecting a student, load their frame history into the renderer.
  const handleSelectStudent = (seat: string) => {
    telemetry.setSelectedSeat(seat);
    setActiveTab("3d"); // Always start in 3D view for new student
    const entry = telemetry.roster[seat];
    if (entry) {
      renderer.loadHistory(entry.frameHistory);
    }
  };

  // Compute post-weld analysis when we have frames for the selected student
  const analysis = useMemo(() => {
    if (!telemetry.selectedSeat) return null;
    const entry = telemetry.roster[telemetry.selectedSeat];
    if (!entry || entry.frameHistory.length < 10) return null;
    return analyzeWeld(entry.frameHistory, telemetry.selectedSeat);
  }, [telemetry.selectedSeat, telemetry.roster]);

  const selectedEntry =
    telemetry.selectedSeat
      ? telemetry.roster[telemetry.selectedSeat] ?? null
      : null;

  return (
    <div className="flex h-screen bg-weld-bg text-weld-text">
      {/* Left: Roster Sidebar */}
      <RosterSidebar
        rosterList={telemetry.rosterList}
        activeCount={telemetry.activeCount}
        connected={telemetry.connected}
        brokerHost={telemetry.brokerHost}
        selectedSeat={telemetry.selectedSeat}
        onSelectStudent={handleSelectStudent}
        footer={
          <BrokerConnector
            brokerHost={telemetry.brokerHost}
            connected={telemetry.connected}
            onHostChange={telemetry.setBrokerHost}
            onConnect={telemetry.connect}
            onDisconnect={telemetry.disconnect}
          />
        }
      />

      {/* Right: Tab-switched viewport */}
      <div className="flex-1 flex flex-col">
        {/* Tab Bar */}
        <div className="flex border-b border-weld-border bg-weld-surface">
          <button
            onClick={() => setActiveTab("3d")}
            className={`px-5 py-2.5 text-sm font-medium transition-colors ${
              activeTab === "3d"
                ? "text-weld-accent border-b-2 border-weld-accent"
                : "text-weld-muted hover:text-weld-text"
            }`}
          >
            🎥 Live 3D View
          </button>
          <button
            onClick={() => setActiveTab("analysis")}
            className={`px-5 py-2.5 text-sm font-medium transition-colors ${
              activeTab === "analysis"
                ? "text-weld-accent border-b-2 border-weld-accent"
                : "text-weld-muted hover:text-weld-text"
            }`}
          >
            🔬 Post-Weld Analysis
            {analysis && (
              <span
                className={`ml-2 px-1.5 py-0.5 rounded text-xs ${
                  analysis.overallScore >= 85
                    ? "bg-green-900/50 text-weld-green"
                    : analysis.overallScore >= 60
                    ? "bg-yellow-900/50 text-weld-yellow"
                    : "bg-red-900/50 text-weld-red"
                }`}
              >
                {analysis.overallScore}%
              </span>
            )}
          </button>
        </div>

        {/* Content */}
        {activeTab === "3d" ? (
          <WeldViewport
            containerRef={renderer.containerRef}
            selectedSeat={telemetry.selectedSeat}
            selectedEntry={selectedEntry}
            isPlaying={renderer.isPlaying}
            currentFrame={renderer.currentFrame}
            totalFrames={renderer.totalFrames}
            onPlay={renderer.play}
            onPause={renderer.pause}
            onReset={renderer.reset}
            onSpeedChange={renderer.setSpeed}
          />
        ) : (
          <WeldAnalysis
            analysis={analysis}
            studentName={selectedEntry?.name ?? ""}
            seat={telemetry.selectedSeat ?? ""}
            onClose={() => setActiveTab("3d")}
          />
        )}
      </div>
    </div>
  );
}
