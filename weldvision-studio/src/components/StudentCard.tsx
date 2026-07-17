/**
 * StudentCard — Clickable roster card showing live KPI for one student.
 */

import type { RosterEntry } from "../lib/types";

interface StudentCardProps {
  entry: RosterEntry;
  selected: boolean;
  onClick: () => void;
}

export function StudentCard({ entry, selected, onClick }: StudentCardProps) {
  const frame = entry.latestFrame;
  const statusClass = entry.isWelding ? "bg-weld-green shadow-[0_0_6px_#3fb950]" : "bg-weld-yellow";
  const scoreClass =
    entry.score >= 85 ? "text-weld-green" : entry.score >= 60 ? "text-weld-yellow" : "text-weld-red";

  return (
    <div
      onClick={onClick}
      className={`
        bg-weld-card border rounded-lg p-3 mb-2 cursor-pointer transition-colors
        ${selected ? "border-weld-accent bg-[#1c2a3e]" : "border-weld-border hover:border-weld-accent"}
      `}
    >
      {/* Header row */}
      <div className="flex justify-between items-center mb-1.5">
        <div>
          <div className="text-xxs text-weld-muted uppercase">{entry.seat}</div>
          <div className="font-semibold text-sm">{entry.name}</div>
        </div>
        <div className={`w-2 h-2 rounded-full ${statusClass}`} title={entry.isWelding ? "Welding" : "Idle"} />
      </div>

      {/* KPIs */}
      {frame && (
        <>
          <div className="flex justify-between text-xxs text-weld-muted">
            <span>Speed</span>
            <span>{frame.travel.speed.toFixed(1)} mm/s</span>
          </div>
          <div className="flex justify-between text-xxs text-weld-muted">
            <span>Progress</span>
            <span>{(frame.travel.progress * 100).toFixed(0)}%</span>
          </div>
        </>
      )}

      {/* Score */}
      <div className="flex justify-between items-center mt-1.5">
        <span className="text-xxs text-weld-muted">Score</span>
        <span className={`text-lg font-semibold ${scoreClass}`}>{entry.score}%</span>
      </div>

      {/* Defect warning */}
      {entry.defect && (
        <div className="text-xxs text-weld-red mt-1">⚠ {entry.defect}</div>
      )}
    </div>
  );
}
