/**
 * RosterSidebar — Student roster panel with live connection status.
 */

import type { RosterEntry } from "../lib/types";
import { StudentCard } from "./StudentCard";
import type { ReactNode } from "react";

interface RosterSidebarProps {
  rosterList: RosterEntry[];
  activeCount: number;
  connected: boolean;
  brokerHost: string;
  selectedSeat: string | null;
  onSelectStudent: (seat: string) => void;
  footer: ReactNode;
}

export function RosterSidebar({
  rosterList,
  activeCount,
  connected,
  brokerHost,
  selectedSeat,
  onSelectStudent,
  footer,
}: RosterSidebarProps) {
  return (
    <div className="w-72 min-w-72 bg-weld-surface border-r border-weld-border flex flex-col z-10">
      {/* Header */}
      <div className="p-4 border-b border-weld-border">
        <h2 className="text-base font-semibold text-weld-accent">⚡ WeldVision Command</h2>
        <p className="text-xs text-weld-muted mt-0.5">Classroom Roster</p>
        <div className="flex items-center gap-1.5 mt-2 text-xxs text-weld-muted">
          <span
            className={`w-2 h-2 rounded-full ${connected ? "bg-weld-green" : "bg-weld-red"}`}
          />
          <span>{connected ? `Connected to ${brokerHost}` : "Disconnected"}</span>
          <span className="ml-auto">{activeCount} active</span>
        </div>
      </div>

      {/* Student List */}
      <div className="flex-1 overflow-y-auto p-2">
        {rosterList.length === 0 ? (
          <div className="text-center text-weld-muted py-8 px-4 text-sm">
            <p>Waiting for students...</p>
            <p className="text-xs mt-1">
              Connect phones to{" "}
              <code className="text-weld-accent bg-weld-card px-1 rounded">
                {brokerHost}
              </code>
            </p>
          </div>
        ) : (
          rosterList.map((entry) => (
            <StudentCard
              key={entry.seat}
              entry={entry}
              selected={entry.seat === selectedSeat}
              onClick={() => onSelectStudent(entry.seat)}
            />
          ))
        )}
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-weld-border">{footer}</div>
    </div>
  );
}
