/**
 * WeldAnalysis — Post-weld defect & distortion analysis display.
 *
 * Shows scores, metrics, detected defects, and coaching recommendations
 * after a student completes their weld session.
 */

import type { WeldAnalysisResult, DefectFinding, MetricResult } from "../lib/analysis-engine";

interface WeldAnalysisProps {
  analysis: WeldAnalysisResult | null;
  studentName: string;
  seat: string;
  onClose: () => void;
}

// ── Score Bar ─────────────────────────────────────────────────────

function ScoreBar({ label, score }: { label: string; score: number }) {
  const color = score >= 85 ? "bg-weld-green" : score >= 60 ? "bg-weld-yellow" : "bg-weld-red";
  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-weld-muted w-24">{label}</span>
      <div className="flex-1 h-2 bg-weld-border rounded-full overflow-hidden">
        <div
          className={`h-full ${color} rounded-full transition-all duration-500`}
          style={{ width: `${score}%` }}
        />
      </div>
      <span className="text-xs font-semibold w-8 text-right">{score}</span>
    </div>
  );
}

// ── Metric Row ────────────────────────────────────────────────────

function MetricRow({ metric }: { metric: MetricResult }) {
  const colorMap = {
    excellent: "text-weld-green",
    good: "text-weld-green",
    fair: "text-weld-yellow",
    poor: "text-weld-red",
  };

  return (
    <div className="flex justify-between items-center py-2 border-b border-weld-border text-sm">
      <span className="text-weld-muted">{metric.description}</span>
      <span className={`font-semibold ${colorMap[metric.rating]}`}>
        {metric.value.toFixed(1)} {metric.unit}
        <span className="ml-2 text-xs uppercase">
          ({metric.rating})
        </span>
      </span>
    </div>
  );
}

// ── Defect Card ───────────────────────────────────────────────────

function DefectCard({ defect }: { defect: DefectFinding }) {
  const severityColor = {
    low: "border-weld-yellow bg-yellow-900/20",
    medium: "border-orange-500 bg-orange-900/20",
    high: "border-weld-red bg-red-900/20",
  };

  const labels = {
    porosity: "🫧 Porosity",
    undercut: "🪒 Undercut",
    lack_of_fusion: "🔗 Lack of Fusion",
    spatter: "💥 Spatter",
    distortion: "↯ Distortion",
  };

  return (
    <div
      className={`border rounded-lg p-3 ${severityColor[defect.severity]} text-sm`}
    >
      <div className="flex justify-between items-center mb-1">
        <span className="font-semibold text-weld-text">
          {labels[defect.type]}
        </span>
        <span className="text-xs uppercase text-weld-muted">
          {defect.severity} · confidence {(defect.confidence * 100).toFixed(0)}%
        </span>
      </div>
      <p className="text-weld-muted text-xs">{defect.description}</p>
      <p className="text-weld-muted text-xs mt-1">
        Location: {defect.locationMm.toFixed(0)} mm along seam
      </p>
    </div>
  );
}

// ── Main Component ────────────────────────────────────────────────

export default function WeldAnalysis({
  analysis,
  studentName,
  seat,
  onClose,
}: WeldAnalysisProps) {
  if (!analysis) {
    return (
      <div className="flex-1 flex items-center justify-center text-weld-muted">
        <div className="text-center">
          <span className="text-4xl block mb-3">🔬</span>
          <p className="text-sm">Complete a weld session to see analysis</p>
          <p className="text-xs mt-1">
            Analysis runs automatically when the student finishes welding
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto p-6 bg-weld-bg">
      {/* Header */}
      <div className="flex justify-between items-start mb-6">
        <div>
          <h2 className="text-xl font-semibold text-weld-accent">
            Post-Weld Analysis
          </h2>
          <p className="text-sm text-weld-muted">
            {studentName} · {seat} · {analysis.frameCount} frames ·{" "}
            {analysis.durationSec.toFixed(1)}s
          </p>
        </div>
        <div className="text-right">
          <div
            className={`text-4xl font-bold ${
              analysis.overallScore >= 85
                ? "text-weld-green"
                : analysis.overallScore >= 60
                ? "text-weld-yellow"
                : "text-weld-red"
            }`}
          >
            {analysis.overallScore}%
          </div>
          <div className="text-xs text-weld-muted">Overall Score</div>
        </div>
      </div>

      {/* Score Bars */}
      <div className="bg-weld-surface border border-weld-border rounded-lg p-4 mb-4 space-y-3">
        <h3 className="text-sm font-semibold text-weld-text mb-2">Score Breakdown</h3>
        <ScoreBar label="Arc Length" score={analysis.arcLengthScore} />
        <ScoreBar label="Travel Speed" score={analysis.travelSpeedScore} />
        <ScoreBar label="Torch Angle" score={analysis.angleScore} />
        <ScoreBar label="Path Tracking" score={analysis.pathScore} />
      </div>

      {/* Detailed Metrics */}
      <div className="bg-weld-surface border border-weld-border rounded-lg p-4 mb-4">
        <h3 className="text-sm font-semibold text-weld-text mb-2">Detailed Metrics</h3>
        <MetricRow metric={analysis.metrics.arcLengthStability} />
        <MetricRow metric={analysis.metrics.travelSpeedUniformity} />
        <MetricRow metric={analysis.metrics.angleConsistency} />
        <MetricRow metric={analysis.metrics.pathDeviation} />
        <MetricRow metric={analysis.metrics.distortion} />
      </div>

      {/* Defects */}
      {analysis.defects.length > 0 && (
        <div className="mb-4">
          <h3 className="text-sm font-semibold text-weld-text mb-2">
            Detected Defects ({analysis.defects.length})
          </h3>
          <div className="space-y-2">
            {analysis.defects.map((d, i) => (
              <DefectCard key={i} defect={d} />
            ))}
          </div>
        </div>
      )}

      {/* Recommendation */}
      <div className="bg-blue-900/20 border border-blue-500/30 rounded-lg p-4">
        <h3 className="text-sm font-semibold text-weld-accent mb-2">
          🎯 Coaching Recommendation
        </h3>
        <p className="text-sm text-weld-text leading-relaxed">
          {analysis.recommendation}
        </p>
      </div>
    </div>
  );
}
