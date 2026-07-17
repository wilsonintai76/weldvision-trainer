#include "tcp_solver.h"

// AprilTag's matd library (compiled into apriltag_core)
extern "C" {
#include "common/matd.h"
}

#include <algorithm>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// ───────────────────────────────────────────────────────
// Coverage: angular spread of Z-axes as percentage
// ───────────────────────────────────────────────────────

double PivotTcpSolver::computeCoverage(const std::vector<Pose>& poses) {
    const size_t n = poses.size();
    if (n < 2) return 0.0;

    // Collect forward (Z) axes from each pose
    std::vector<Vec3> axes;
    axes.reserve(n);
    for (const auto& p : poses) {
        axes.emplace_back(p.R(0,2), p.R(1,2), p.R(2,2));
    }

    // Mean axis direction
    Vec3 mean(0.0, 0.0, 0.0);
    for (const auto& a : axes) mean += a;
    mean = mean / static_cast<double>(n);

    // Maximum angular deviation from the mean
    double maxAngleDeg = 0.0;
    for (const auto& a : axes) {
        double dot = std::max(-1.0, std::min(1.0, mean.dot(a)));
        double angleDeg = std::acos(dot) * 180.0 / M_PI;
        if (angleDeg > maxAngleDeg) maxAngleDeg = angleDeg;
    }

    // Normalise: 30° half-angle = 100% coverage
    return std::min(100.0, (maxAngleDeg / 30.0) * 100.0);
}

// ───────────────────────────────────────────────────────
// Condition number via SVD (uses AprilTag's matd)
// ───────────────────────────────────────────────────────

double PivotTcpSolver::conditionNumber(const double* A_data, int rows, int cols) {
    // Build matd matrix from raw data (column-major in matd)
    matd_t* A = matd_create(rows, cols);
    int idx = 0;
    for (int c = 0; c < cols; ++c) {
        for (int r = 0; r < rows; ++r) {
            MATD_EL(A, r, c) = A_data[idx++];
        }
    }

    // Compute SVD
    matd_svd_t svd = matd_svd(A);

    double cond = 1e10; // Default: singular
    if (svd.S && svd.S->nrows > 0 && svd.S->ncols > 0) {
        int k = std::min(svd.S->nrows, svd.S->ncols);
        double sMax = MATD_EL(svd.S, 0, 0);
        double sMin = MATD_EL(svd.S, k - 1, 0);
        cond = (sMin > 1e-10) ? (sMax / sMin) : 1e10;
    }

    matd_svd_destroy(&svd);
    matd_destroy(A);
    return cond;
}

// ───────────────────────────────────────────────────────
// Reprojection error: how well the tip converges to a point
// ───────────────────────────────────────────────────────

double PivotTcpSolver::reprojectionError(
    const std::vector<Pose>& poses,
    const Vec3& tcp
) {
    // Transform tip into world space for each pose
    std::vector<Vec3> worldPoints;
    worldPoints.reserve(poses.size());

    for (const auto& p : poses) {
        worldPoints.push_back(p.R * tcp + p.t);
    }

    // Centroid of all world-space tip estimates
    Vec3 centroid(0.0, 0.0, 0.0);
    for (const auto& wp : worldPoints) centroid += wp;
    centroid = centroid / static_cast<double>(worldPoints.size());

    // Mean deviation from centroid
    double sum = 0.0;
    for (const auto& wp : worldPoints) {
        double dx = wp.x - centroid.x;
        double dy = wp.y - centroid.y;
        double dz = wp.z - centroid.z;
        sum += std::sqrt(dx*dx + dy*dy + dz*dz);
    }
    return sum / static_cast<double>(worldPoints.size());
}

// ───────────────────────────────────────────────────────
// Main solver
// ───────────────────────────────────────────────────────

TcpResult PivotTcpSolver::solve(const std::vector<Pose>& poses) {
    TcpResult result{};
    const size_t n = poses.size();

    result.quality.sampleCount = static_cast<int>(n);
    if (n < 5) return result;   // Need at least 5 poses

    // Coverage gate
    result.quality.coveragePercent = computeCoverage(poses);
    if (result.quality.coveragePercent < 70.0) return result;

    // ── Build linear system ──────────────────────────
    // For each pair (i, j) of poses:
    //   (R_i - R_j) · tcp = t_j - t_i
    // Stack into A · tcp = b
    //
    // Store A in column-major order for matd

    const size_t pairCount = (n * (n - 1)) / 2;
    const int totalRows = static_cast<int>(pairCount * 3);
    const int totalCols = 3;
    const int dataLen = totalRows * totalCols;

    double* A_data = new double[dataLen];
    double* b_data = new double[totalRows];

    int row = 0;
    for (size_t i = 0; i < n; ++i) {
        const Mat33& Ri = poses[i].R;
        const Vec3&  ti = poses[i].t;

        for (size_t j = i + 1; j < n; ++j) {
            const Mat33& Rj = poses[j].R;
            const Vec3&  tj = poses[j].t;

            // Row 0: (R_i - R_j)[0] · tcp = t_j[0] - t_i[0]
            A_data[row * 3 + 0] = Ri(0,0) - Rj(0,0);
            A_data[row * 3 + 1] = Ri(0,1) - Rj(0,1);
            A_data[row * 3 + 2] = Ri(0,2) - Rj(0,2);
            b_data[row] = tj.x - ti.x;
            row++;

            // Row 1
            A_data[row * 3 + 0] = Ri(1,0) - Rj(1,0);
            A_data[row * 3 + 1] = Ri(1,1) - Rj(1,1);
            A_data[row * 3 + 2] = Ri(1,2) - Rj(1,2);
            b_data[row] = tj.y - ti.y;
            row++;

            // Row 2
            A_data[row * 3 + 0] = Ri(2,0) - Rj(2,0);
            A_data[row * 3 + 1] = Ri(2,1) - Rj(2,1);
            A_data[row * 3 + 2] = Ri(2,2) - Rj(2,2);
            b_data[row] = tj.z - ti.z;
            row++;
        }
    }

    // Condition number before solving (uses first 3 cols)
    // This is the primary guard against degenerate pivot motion.
    result.quality.conditionNumber = conditionNumber(A_data, totalRows, totalCols);

    // ── Solve via SVD using matd ─────────────────────
    // Build column-major matd matrices

    matd_t* A_mat = matd_create(totalRows, totalCols);
    matd_t* b_mat = matd_create(totalRows, 1);

    int idx = 0;
    for (int c = 0; c < totalCols; ++c) {
        for (int r = 0; r < totalRows; ++r) {
            MATD_EL(A_mat, r, c) = A_data[idx++];
        }
    }
    for (int r = 0; r < totalRows; ++r) {
        MATD_EL(b_mat, r, 0) = b_data[r];
    }

    // SVD: A = U * S * V'
    matd_svd_t svd = matd_svd(A_mat);

    // Solve: x = V * S^+ * U' * b
    // For each singular value s_i > threshold, x += (u_i'·b / s_i) * v_i
    int k = std::min(svd.S->nrows, svd.S->ncols);

    double x[3] = {0.0, 0.0, 0.0};

    for (int i = 0; i < k; ++i) {
        double si = MATD_EL(svd.S, i, 0);
        if (si < 1e-10) continue;

        // u_i' · b
        double ui_dot_b = 0.0;
        for (int r = 0; r < totalRows; ++r) {
            ui_dot_b += MATD_EL(svd.U, r, i) * MATD_EL(b_mat, r, 0);
        }

        double alpha = ui_dot_b / si;
        for (int c = 0; c < totalCols; ++c) {
            x[c] += alpha * MATD_EL(svd.V, c, i);
        }
    }

    result.offset = Vec3(x[0], x[1], x[2]);

    // ── Residual ─────────────────────────────────────
    double residualSq = 0.0;
    for (int r = 0; r < totalRows; ++r) {
        double pred = 0.0;
        for (int c = 0; c < totalCols; ++c) {
            pred += MATD_EL(A_mat, r, c) * x[c];
        }
        double diff = pred - MATD_EL(b_mat, r, 0);
        residualSq += diff * diff;
    }
    result.quality.residualMm =
        std::sqrt(residualSq / static_cast<double>(pairCount));

    // ── Reprojection errors ──────────────────────────
    result.quality.reprojectionMeanMm = reprojectionError(poses, result.offset);
    result.quality.reprojectionMaxMm  = 0.0;

    // Cleanup
    matd_svd_destroy(&svd);
    matd_destroy(A_mat);
    matd_destroy(b_mat);
    delete[] A_data;
    delete[] b_data;

    return result;
}
