/**
 * WeldVision Trainer 2.0 — Analysis Engine
 * =========================================
 *
 * Client-side post-weld analysis. Takes a student's frame history
 * (StudentTelemetryFrame[]) and computes:
 *
 *   • Arc Length Stability    — standard deviation of CTWD (Z) across frames
 *   • Travel Speed Uniformity — coefficient of variation of inter-frame travel
 *   • Angle Consistency       — std dev of work/travel angles
 *   • Path Deviation          — max lateral drift from ideal seam center
 *   • Porosity Risk           — high-frequency Z oscillation detection
 *   • Distortion               — progressive drift in Z over time (thermal bow)
 *   • Defect Classification   — rule-based pass/fail for each metric
 *
 * All spatial values in mm, angles in degrees.
 */

import type { StudentTelemetryFrame } from "./types";

// ── Analysis Output Types ────────────────────────────────────────────────────

export interface MetricResult {
  value: number;
  unit: string;
  rating: "excellent" | "good" | "fair" | "poor";
  threshold: { good: number; fair: number };
  description: string;
}

export interface DefectFinding {
  type: "porosity" | "undercut" | "lack_of_fusion" | "spatter" | "distortion";
  severity: "low" | "medium" | "high";
  confidence: number; // 0–1
  locationMm: number; // travel distance where detected
  description: string;
}

export interface WeldAnalysisResult {
  sessionId: string;
  frameCount: number;
  durationSec: number;

  // Scores (0–100)
  arcLengthScore: number;
  travelSpeedScore: number;
  angleScore: number;
  pathScore: number;
  overallScore: number;

  // Detailed metrics
  metrics: {
    arcLengthStability: MetricResult;
    travelSpeedUniformity: MetricResult;
    angleConsistency: MetricResult;
    pathDeviation: MetricResult;
    distortion: MetricResult;
  };

  // Summary
  defects: DefectFinding[];
  recommendation: string;
}

// ── Thresholds ───────────────────────────────────────────────────────────────

const LIMITS = {
  arcLength:      { good: 1.0, fair: 2.5 },   // std dev CTWD in mm
  travelSpeed:    { good: 15,  fair: 35  },   // coefficient of variation %
  angleStability: { good: 3.0, fair: 8.0 },   // std dev in degrees
  pathDeviation:  { good: 1.5, fair: 4.0 },   // max lateral deviation mm
  distortion:     { good: 1.0, fair: 3.0 },   // mm of Z drift over session
};

// ── Helpers ──────────────────────────────────────────────────────────────────

function mean(values: number[]): number {
  return values.reduce((s, v) => s + v, 0) / values.length;
}

function stdDev(values: number[], avg: number): number {
  if (values.length < 2) return 0;
  const variance = values.reduce((s, v) => s + (v - avg) ** 2, 0) / (values.length - 1);
  return Math.sqrt(variance);
}

function rating(value: number, threshold: { good: number; fair: number }): MetricResult["rating"] {
  if (value <= threshold.good) return "excellent";
  if (value <= threshold.fair) return "good";
  if (value <= threshold.fair * 2) return "fair";
  return "poor";
}

function scoreFromRating(r: MetricResult["rating"]): number {
  switch (r) {
    case "excellent": return 90 + Math.random() * 10;
    case "good": return 75 + Math.random() * 15;
    case "fair": return 50 + Math.random() * 25;
    case "poor": return 10 + Math.random() * 40;
  }
}

// ── Defect Detectors ─────────────────────────────────────────────────────────

function detectPorosity(frames: StudentTelemetryFrame[], avgZ: number): DefectFinding | null {
  const zValues = frames.map((f) => f.spatial.z);
  let highFreqOscillations = 0;

  for (let i = 2; i < zValues.length; i++) {
    const dz1 = zValues[i] - zValues[i - 1];
    const dz2 = zValues[i - 1] - zValues[i - 2];
    // High-frequency sign change = oscillation
    if (dz1 * dz2 < 0 && Math.abs(dz1) > 0.5 && Math.abs(dz2) > 0.5) {
      highFreqOscillations++;
    }
  }

  const ratio = highFreqOscillations / Math.max(frames.length, 1);
  if (ratio > 0.05) {
    return {
      type: "porosity",
      severity: ratio > 0.1 ? "high" : "medium",
      confidence: Math.min(ratio * 10, 1),
      locationMm: frames[Math.floor(frames.length * 0.5)]?.travel.distanceMm ?? 0,
      description: `Detected ${(ratio * 100).toFixed(1)}% high-frequency arc-length oscillation — possible porosity`,
    };
  }
  return null;
}

function detectUndercut(frames: StudentTelemetryFrame[]): DefectFinding | null {
  // Undercut = excessive lateral deviation at seam edges
  const deviations = frames.map((f) => Math.abs(f.spatial.x));
  const maxDev = Math.max(...deviations);

  if (maxDev > 4.0) {
    return {
      type: "undercut",
      severity: maxDev > 6 ? "high" : "medium",
      confidence: Math.min((maxDev - 3) / 4, 1),
      locationMm: frames[deviations.indexOf(maxDev)]?.travel.distanceMm ?? 0,
      description: `Maximum lateral deviation of ${maxDev.toFixed(1)} mm — possible undercut`,
    };
  }
  return null;
}

function detectLackOfFusion(frames: StudentTelemetryFrame[]): DefectFinding | null {
  // Lack of fusion = too low CTWD (torch too far from plate)
  const zValues = frames.map((f) => f.spatial.z);
  const avgZ = mean(zValues);

  if (avgZ > 8.0) {
    return {
      type: "lack_of_fusion",
      severity: avgZ > 12 ? "high" : "medium",
      confidence: Math.min((avgZ - 6) / 6, 1),
      locationMm: frames[Math.floor(frames.length / 2)]?.travel.distanceMm ?? 0,
      description: `Mean CTWD of ${avgZ.toFixed(1)} mm — torch too far from plate, risk of lack of fusion`,
    };
  }
  return null;
}

function detectDistortion(frames: StudentTelemetryFrame[]): DefectFinding | null {
  // Distortion = progressive Z drift over time (thermal bowing)
  const zValues = frames.map((f) => f.spatial.z);
  if (zValues.length < 10) return null;

  const firstThird = zValues.slice(0, Math.floor(zValues.length / 3));
  const lastThird = zValues.slice(Math.floor((2 * zValues.length) / 3));
  const drift = Math.abs(mean(lastThird) - mean(firstThird));

  if (drift > 2.0) {
    return {
      type: "distortion",
      severity: drift > 4 ? "high" : "medium",
      confidence: Math.min(drift / 5, 1),
      locationMm: frames[frames.length - 1]?.travel.distanceMm ?? 0,
      description: `Progressive Z drift of ${drift.toFixed(1)} mm detected — thermal distortion likely`,
    };
  }
  return null;
}

// ── Main Analysis Function ───────────────────────────────────────────────────

export function analyzeWeld(
  frames: StudentTelemetryFrame[],
  sessionId: string = "unknown"
): WeldAnalysisResult | null {
  if (frames.length < 10) return null;

  const durationSec =
    (frames[frames.length - 1].timestamp - frames[0].timestamp) / 1000;

  // ── Arc Length Stability (CTWD Z) ──────────────────
  const zValues = frames.map((f) => f.spatial.z);
  const avgZ = mean(zValues);
  const stdZ = stdDev(zValues, avgZ);

  const arcLengthMetric: MetricResult = {
    value: stdZ,
    unit: "mm",
    rating: rating(stdZ, LIMITS.arcLength),
    threshold: LIMITS.arcLength,
    description: `CTWD standard deviation: ${stdZ.toFixed(2)} mm`,
  };

  // ── Travel Speed Uniformity ─────────────────────────
  const speeds = frames.map((f) => f.travel.speed);
  const avgSpeed = mean(speeds);
  const stdSpeed = stdDev(speeds, avgSpeed);
  const cvSpeed = avgSpeed > 0 ? (stdSpeed / avgSpeed) * 100 : 0;

  const travelSpeedMetric: MetricResult = {
    value: cvSpeed,
    unit: "%",
    rating: rating(cvSpeed, LIMITS.travelSpeed),
    threshold: LIMITS.travelSpeed,
    description: `Speed variation coefficient: ${cvSpeed.toFixed(1)}%`,
  };

  // ── Angle Consistency ───────────────────────────────
  const workAngles = frames.map((f) => f.angles.work);
  const travelAngles = frames.map((f) => f.angles.travel);
  const avgWorkA = mean(workAngles);
  const avgTravelA = mean(travelAngles);
  const stdWork = stdDev(workAngles, avgWorkA);
  const stdTravel = stdDev(travelAngles, avgTravelA);
  const combinedStd = Math.sqrt(stdWork ** 2 + stdTravel ** 2) / 2;

  const angleMetric: MetricResult = {
    value: combinedStd,
    unit: "°",
    rating: rating(combinedStd, LIMITS.angleStability),
    threshold: LIMITS.angleStability,
    description: `Combined angle std dev: ${combinedStd.toFixed(1)}° (work: ${stdWork.toFixed(1)}°, travel: ${stdTravel.toFixed(1)}°)`,
  };

  // ── Path Deviation ──────────────────────────────────
  const lateralDeviations = frames.map((f) => Math.abs(f.spatial.x));
  const maxLatDev = Math.max(...lateralDeviations);

  const pathMetric: MetricResult = {
    value: maxLatDev,
    unit: "mm",
    rating: rating(maxLatDev, LIMITS.pathDeviation),
    threshold: LIMITS.pathDeviation,
    description: `Maximum lateral deviation from seam center: ${maxLatDev.toFixed(1)} mm`,
  };

  // ── Distortion (Z drift) ────────────────────────────
  const drift = (() => {
    const n = zValues.length;
    const t1 = zValues.slice(0, Math.floor(n / 3));
    const t3 = zValues.slice(Math.floor((2 * n) / 3));
    if (t1.length === 0 || t3.length === 0) return 0;
    return Math.abs(mean(t3) - mean(t1));
  })();

  const distortionMetric: MetricResult = {
    value: drift,
    unit: "mm",
    rating: rating(drift, LIMITS.distortion),
    threshold: LIMITS.distortion,
    description: `CTWD drift from start to end: ${drift.toFixed(1)} mm`,
  };

  // ── Scores ──────────────────────────────────────────
  const arcScore = scoreFromRating(arcLengthMetric.rating);
  const speedScore = scoreFromRating(travelSpeedMetric.rating);
  const angleScore = scoreFromRating(angleMetric.rating);
  const pathScore = scoreFromRating(pathMetric.rating);
  const overallScore = Math.round(
    arcScore * 0.3 + speedScore * 0.3 + angleScore * 0.2 + pathScore * 0.2
  );

  // ── Defect Detection ────────────────────────────────
  const defects: DefectFinding[] = [];
  const porosity = detectPorosity(frames, avgZ);
  const undercut = detectUndercut(frames);
  const lackOfFusion = detectLackOfFusion(frames);
  const distortion = detectDistortion(frames);

  if (porosity) defects.push(porosity);
  if (undercut) defects.push(undercut);
  if (lackOfFusion) defects.push(lackOfFusion);
  if (distortion) defects.push(distortion);

  // ── Recommendation ──────────────────────────────────
  const recommendation = generateRecommendation(
    {
      arcLengthStability: arcLengthMetric,
      travelSpeedUniformity: travelSpeedMetric,
      angleConsistency: angleMetric,
      pathDeviation: pathMetric,
      distortion: distortionMetric,
    },
    defects
  );

  return {
    sessionId,
    frameCount: frames.length,
    durationSec,

    arcLengthScore: Math.round(arcScore),
    travelSpeedScore: Math.round(speedScore),
    angleScore: Math.round(angleScore),
    pathScore: Math.round(pathScore),
    overallScore,

    metrics: {
      arcLengthStability: arcLengthMetric,
      travelSpeedUniformity: travelSpeedMetric,
      angleConsistency: angleMetric,
      pathDeviation: pathMetric,
      distortion: distortionMetric,
    },

    defects,
    recommendation,
  };
}

// ── Recommendation Generator ─────────────────────────────────────────────────

function generateRecommendation(
  metrics: WeldAnalysisResult["metrics"],
  defects: DefectFinding[]
): string {
  const parts: string[] = [];

  if (defects.length > 0) {
    parts.push(`Found ${defects.length} defect pattern(s)`);
    for (const d of defects) {
      const advice = defectAdvice(d.type);
      if (advice) parts.push(advice);
    }
  }

  if (metrics.arcLengthStability.rating === "poor") {
    parts.push("Practice maintaining consistent arc length — keep nozzle 3-6 mm from the plate");
  }
  if (metrics.travelSpeedUniformity.rating === "poor") {
    parts.push("Focus on steady hand travel speed — avoid pausing or rushing");
  }
  if (metrics.angleConsistency.rating === "poor") {
    parts.push("Maintain torch angle at 10° travel, 90° work throughout the pass");
  }
  if (metrics.pathDeviation.rating === "poor") {
    parts.push("Keep the torch centered on the seam — use the root crease as a visual guide");
  }
  if (metrics.distortion.rating === "poor") {
    parts.push("Apply tack welds at both ends to reduce thermal distortion");
  }

  if (parts.length === 0) return "Excellent weld — no significant issues detected. Keep practicing to maintain consistency.";
  return parts.join(". ") + ".";
}

function defectAdvice(type: DefectFinding["type"]): string | null {
  switch (type) {
    case "porosity":
      return "Check gas flow rate and ensure nozzle is clean — high-frequency arc oscillation detected";
    case "undercut":
      return "Reduce travel speed at seam edges and maintain 90° work angle to avoid undercut";
    case "lack_of_fusion":
      return "Reduce CTWD — the torch is too far from the plate, causing insufficient penetration";
    case "distortion":
      return "Use intermittent welding technique or increase tack welds to control thermal distortion";
    case "spatter":
      return "Reduce voltage slightly and ensure wire feed speed is matched to travel speed";
    default:
      return null;
  }
}
