import React, { useState } from "react";

// ── Types ────────────────────────────────────────────

interface TcpQuality {
  coveragePercent: number;
  conditionNumber: number;
  residualMm: number;
  reprojectionMeanMm: number;
  reprojectionMaxMm?: number;
  sampleCount: number;
}

interface TcpProfile {
  offsetX: number;
  offsetY: number;
  offsetZ: number;
  quality: TcpQuality;
}

interface WorkpieceProfile {
  method: "touchdown" | "tag_auto";
  seamLengthMm: number;
  valid: boolean;
}

interface CalibrationProfileMessage {
  type: "calibration_profile";
  bracketId: string;
  studentId: string;
  deviceModel: string;
  profile: {
    tcp: TcpProfile;
    workpiece: WorkpieceProfile;
  };
}

interface CalibrationGateProps {
  /** Full calibration profile from the telemetry server, or null if not yet received */
  calibrationData: CalibrationProfileMessage | null;
  /** Called when instructor approves and wants to start the session */
  onProceed: () => void;
  /** Called when instructor requests recalibration with a reason */
  onRequestRecalibration: (reason: string) => void;
}

// ── Thresholds ───────────────────────────────────────

const THRESHOLDS = {
  coveragePercent: { min: 70, label: "≥ 70%" },
  conditionNumber: { max: 100, label: "≤ 100" },
  residualMm: { max: 0.5, label: "≤ 0.5 mm" },
  reprojectionMeanMm: { max: 0.3, label: "≤ 0.3 mm" },
  sampleCount: { min: 5, label: "≥ 5" },
} as const;

// ── Status Row Component ─────────────────────────────

function StatusRow({
  label,
  value,
  unit = "",
  threshold,
  inverse = false,
  tooltip,
}: {
  label: string;
  value: number | string;
  unit?: string;
  threshold?: { min?: number; max?: number; label: string };
  inverse?: boolean;
  tooltip?: string;
}) {
  let passed = true;

  if (threshold && typeof value === "number") {
    if (threshold.min !== undefined) passed = value >= threshold.min;
    if (threshold.max !== undefined) passed = passed && value <= threshold.max;
    if (inverse) passed = !passed;
  }

  const displayValue =
    typeof value === "number"
      ? Number.isInteger(value)
        ? value.toString()
        : value.toFixed(1)
      : value;

  return (
    <div className="flex justify-between items-center py-2 border-b border-gray-100 last:border-b-0">
      <div className="flex items-center gap-1">
        <span className="text-gray-600 text-sm">{label}</span>
        {tooltip && (
          <span className="text-gray-400 cursor-help text-xs" title={tooltip}>
            ⓘ
          </span>
        )}
      </div>
      <div className="flex items-center gap-2">
        <span
          className={`font-medium text-sm ${
            passed ? "text-green-600" : "text-red-600"
          }`}
        >
          {displayValue}
          {unit && <span className="text-gray-400 ml-0.5">{unit}</span>}
        </span>
        {!passed && threshold && (
          <span className="text-xs text-red-400 bg-red-50 px-1.5 py-0.5 rounded">
            need {threshold.label}
          </span>
        )}
        {passed && (
          <span className="text-green-400 text-xs">✓</span>
        )}
      </div>
    </div>
  );
}

// ── Badge Component ──────────────────────────────────

function Badge({
  variant,
  children,
}: {
  variant: "green" | "red" | "amber";
  children: React.ReactNode;
}) {
  const styles = {
    green: "bg-green-50 border-green-200 text-green-700",
    red: "bg-red-50 border-red-200 text-red-700",
    amber: "bg-amber-50 border-amber-200 text-amber-700",
  };

  return (
    <span
      className={`inline-block border rounded-md px-3 py-1 text-sm font-medium ${styles[variant]}`}
    >
      {children}
    </span>
  );
}

// ── Main Component ───────────────────────────────────

export default function CalibrationGate({
  calibrationData,
  onProceed,
  onRequestRecalibration,
}: CalibrationGateProps) {
  const [recalReason, setRecalReason] = useState("");
  const [showReasonInput, setShowReasonInput] = useState(false);

  const tcp = calibrationData?.profile?.tcp;
  const workpiece = calibrationData?.profile?.workpiece;
  const tcpQuality = tcp?.quality ?? null;

  // Evaluate gates
  const tcpPassed =
    tcpQuality != null &&
    tcpQuality.coveragePercent >= (THRESHOLDS.coveragePercent.min ?? 0) &&
    tcpQuality.conditionNumber <= (THRESHOLDS.conditionNumber.max ?? Infinity) &&
    tcpQuality.residualMm <= (THRESHOLDS.residualMm.max ?? Infinity) &&
    tcpQuality.reprojectionMeanMm <= (THRESHOLDS.reprojectionMeanMm.max ?? Infinity) &&
    tcpQuality.sampleCount >= (THRESHOLDS.sampleCount.min ?? 0);

  const workpiecePassed = workpiece?.valid === true;

  const allPassed = tcpPassed && workpiecePassed;

  // ── Loading state ──────────────────────────────────

  if (calibrationData == null) {
    return (
      <div className="bg-white rounded-lg shadow-md p-6 max-w-md">
        <h3 className="text-lg font-semibold mb-4">Calibration Gate</h3>
        <div className="flex flex-col items-center py-8 text-gray-400">
          <svg
            className="animate-spin h-8 w-8 mb-3"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
          <p className="text-sm">Waiting for calibration data...</p>
          <p className="text-xs mt-1">
            Ask the student to complete calibration on their device.
          </p>
        </div>
      </div>
    );
  }

  // ── Calibration data received ──────────────────────

  return (
    <div className="bg-white rounded-lg shadow-md p-6 max-w-md">
      {/* Header */}
      <div className="flex justify-between items-start mb-4">
        <div>
          <h3 className="text-lg font-semibold">Calibration Gate</h3>
          <p className="text-xs text-gray-500">
            {calibrationData.bracketId} / {calibrationData.studentId}
            {" · "}
            {calibrationData.deviceModel}
          </p>
        </div>
        <Badge variant={allPassed ? "green" : "red"}>
          {allPassed ? "Ready" : "Blocked"}
        </Badge>
      </div>

      {/* TCP Quality Section */}
      <div className="mb-4">
        <h4 className="text-sm font-medium text-gray-700 mb-2">
          TCP Pivot Calibration
        </h4>
        {tcpQuality == null ? (
          <p className="text-amber-600 text-sm">TCP calibration not performed.</p>
        ) : (
          <div className="bg-gray-50 rounded-md px-3 py-1">
            <StatusRow
              label="Pivot Coverage"
              value={tcpQuality.coveragePercent}
              unit="%"
              threshold={THRESHOLDS.coveragePercent}
              tooltip="Angular spread of the torch during pivot. A wider cone produces a more reliable TCP offset."
            />
            <StatusRow
              label="Condition Number"
              value={tcpQuality.conditionNumber}
              threshold={THRESHOLDS.conditionNumber}
              inverse
              tooltip="Measures how well-posed the linear system is. High values indicate degenerate pivot motion (e.g. rolling without tilting). This is the primary guard against a wrong TCP offset."
            />
            <StatusRow
              label="Residual"
              value={tcpQuality.residualMm}
              unit="mm"
              threshold={THRESHOLDS.residualMm}
              inverse
              tooltip="Least-squares fitting error. Measures measurement noise."
            />
            <StatusRow
              label="Reprojection Error"
              value={tcpQuality.reprojectionMeanMm}
              unit="mm"
              threshold={THRESHOLDS.reprojectionMeanMm}
              inverse
              tooltip="How tightly all tip estimates converge to a single point. Measures sample consistency — catches tip slippage during pivot."
            />
            <StatusRow
              label="Sample Count"
              value={tcpQuality.sampleCount}
              threshold={THRESHOLDS.sampleCount}
              tooltip="Number of poses collected during the pivot motion."
            />
          </div>
        )}
      </div>

      {/* Workpiece Section */}
      <div className="mb-6">
        <h4 className="text-sm font-medium text-gray-700 mb-2">
          Workpiece Frame
        </h4>
        <div className="bg-gray-50 rounded-md px-3 py-1">
          <StatusRow label="Frame Valid" value={workpiecePassed ? "Yes" : "No"} />
          <StatusRow label="Method" value={workpiece?.method ?? "—"} />
          <StatusRow
            label="Seam Length"
            value={workpiece?.seamLengthMm ?? 0}
            unit="mm"
          />
        </div>
      </div>

      {/* TCP Offset Display (informational) */}
      {tcp && (
        <div className="mb-6 bg-blue-50 rounded-md p-3 text-xs text-blue-800">
          <span className="font-medium">TCP Offset (tag → tip):</span>{" "}
          X={tcp.offsetX.toFixed(1)} mm, Y={tcp.offsetY.toFixed(1)} mm, Z={tcp.offsetZ.toFixed(1)} mm
        </div>
      )}

      {/* Action Section */}
      {allPassed ? (
        <div className="bg-green-50 border border-green-200 rounded-md p-4 text-center">
          <p className="text-green-700 font-medium">All checks passed.</p>
          <p className="text-green-600 text-sm mt-1">
            The student is cleared to begin welding.
          </p>
          <button
            onClick={onProceed}
            className="mt-3 w-full bg-green-600 text-white px-4 py-2 rounded-md hover:bg-green-700 transition-colors font-medium"
          >
            Approve &amp; Start Session
          </button>
        </div>
      ) : (
        <div className="bg-red-50 border border-red-200 rounded-md p-4">
          <p className="text-red-700 font-medium text-center">
            Calibration does not meet requirements.
          </p>

          {!showReasonInput ? (
            <button
              onClick={() => setShowReasonInput(true)}
              className="mt-3 w-full bg-red-600 text-white px-4 py-2 rounded-md hover:bg-red-700 transition-colors font-medium"
            >
              Request Recalibration
            </button>
          ) : (
            <div className="mt-3 space-y-2">
              <textarea
                className="w-full border border-gray-300 rounded-md p-2 text-sm"
                rows={2}
                placeholder="Reason for recalibration (sent to student's device)..."
                value={recalReason}
                onChange={(e) => setRecalReason(e.target.value)}
              />
              <div className="flex gap-2">
                <button
                  onClick={() => {
                    onRequestRecalibration(recalReason || "Calibration quality insufficient");
                    setShowReasonInput(false);
                    setRecalReason("");
                  }}
                  className="flex-1 bg-red-600 text-white px-3 py-1.5 rounded-md hover:bg-red-700 text-sm"
                >
                  Send Request
                </button>
                <button
                  onClick={() => setShowReasonInput(false)}
                  className="px-3 py-1.5 border border-gray-300 rounded-md text-sm hover:bg-gray-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Condition number warning */}
      {tcpQuality != null && tcpQuality.conditionNumber > 100 && (
        <p className="text-xs text-gray-400 mt-3 italic leading-relaxed">
          <strong>High condition number detected.</strong> This means the pivot
          motion lacked sufficient angular diversity (e.g., the torch was rolled
          without tilting). Ask the student to repeat the pivot with a wider
          cone — tilt the torch left, right, forward, and back while keeping
          the tip in the dimple.
        </p>
      )}
    </div>
  );
}
