/**
 * WeldViewport — Focused 3D viewport showing the selected student's weld.
 *
 * Renders a Three.js canvas with playback controls and a HUD info panel.
 * Uses the Focused Viewport Pattern: only ONE WebGL context at a time.
 */

import type { RefObject } from "react";
import type { RosterEntry } from "../lib/types";

interface WeldViewportProps {
  containerRef: RefObject<HTMLDivElement | null>;
  selectedSeat: string | null;
  selectedEntry: RosterEntry | null;
  isPlaying: boolean;
  currentFrame: number;
  totalFrames: number;
  onPlay: () => void;
  onPause: () => void;
  onReset: () => void;
  onSpeedChange: (speed: number) => void;
}

export function WeldViewport({
  containerRef,
  selectedSeat,
  selectedEntry,
  isPlaying,
  currentFrame,
  totalFrames,
  onPlay,
  onPause,
  onReset,
  onSpeedChange,
}: WeldViewportProps) {
  const frame = selectedEntry?.latestFrame;

  return (
    <div className="flex-1 flex flex-col">
      {/* Header */}
      <div className="px-5 py-3 border-b border-weld-border bg-weld-surface flex justify-between items-center">
        <h3 className="text-sm text-weld-accent">
          {selectedEntry
            ? `${selectedEntry.name} (${selectedEntry.seat})`
            : "Select a student to begin"}
        </h3>
        <span className="text-xs text-weld-muted">
          {selectedEntry?.isWelding ? "🟢 Welding" : selectedEntry ? "🟡 Idle" : ""}
        </span>
      </div>

      {/* 3D Canvas */}
      <div className="flex-1 relative">
        <div ref={containerRef} className="absolute inset-0" />

        {/* Empty state */}
        {!selectedSeat && (
          <div className="absolute inset-0 flex flex-col items-center justify-center text-weld-muted gap-2 pointer-events-none">
            <span className="text-3xl">🔍</span>
            <span className="text-sm">Click a student in the roster to inspect their weld</span>
          </div>
        )}

        {/* Info overlay */}
        {frame && (
          <div className="absolute top-3 right-3 bg-[rgba(22,27,34,0.88)] backdrop-blur-md border border-weld-border rounded-xl px-4 py-3 text-xs leading-relaxed z-10 min-w-[180px]">
            <div>Frame: <span className="text-weld-accent font-semibold">{currentFrame} / {totalFrames}</span></div>
            <div>Travel θ: <span className="text-weld-accent font-semibold">{frame.angles.travel.toFixed(1)}°</span></div>
            <div>Work φ: <span className="text-weld-accent font-semibold">{frame.angles.work.toFixed(1)}°</span></div>
            <div>Speed: <span className="text-weld-accent font-semibold">{frame.travel.speed.toFixed(1)} mm/s</span></div>
            <div>
              Tip:{" "}
              <span className="text-weld-accent font-semibold">
                ({frame.spatial.x.toFixed(1)}, {frame.spatial.y.toFixed(1)}, {frame.spatial.z.toFixed(1)}) mm
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Playback Controls */}
      <div className="flex justify-center items-center gap-3 py-2.5 px-5 bg-[rgba(22,27,34,0.92)] backdrop-blur-md border-t border-weld-border">
        <button
          onClick={isPlaying ? onPause : onPlay}
          disabled={totalFrames === 0}
          className="bg-weld-green text-white border border-[#2ea043] rounded-lg px-4 py-1.5 text-sm cursor-pointer hover:bg-[#2ea043] disabled:bg-weld-card disabled:text-weld-muted disabled:border-weld-border disabled:cursor-not-allowed transition-colors"
        >
          {isPlaying ? "⏸ Pause" : "▶ Play"}
        </button>
        <button
          onClick={onReset}
          disabled={totalFrames === 0}
          className="bg-weld-card text-weld-text border border-weld-border rounded-lg px-3 py-1.5 text-sm cursor-pointer hover:border-weld-muted disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          ⏮ Reset
        </button>
        <select
          onChange={(e) => onSpeedChange(parseFloat(e.target.value))}
          className="bg-weld-bg text-weld-text border border-weld-border rounded-md px-2 py-1.5 text-sm"
          defaultValue="1"
        >
          <option value="0.25">0.25×</option>
          <option value="0.5">0.5×</option>
          <option value="1">1×</option>
          <option value="2">2×</option>
          <option value="4">4×</option>
        </select>
        <span className="text-xs text-weld-muted">
          {totalFrames > 0 ? `${((currentFrame / totalFrames) * 100).toFixed(0)}%` : "0%"}
        </span>
      </div>
    </div>
  );
}
