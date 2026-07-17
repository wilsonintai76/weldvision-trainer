#ifndef TCP_SOLVER_H
#define TCP_SOLVER_H

#include "../geometry/geometry.h"
#include <vector>

// ───────────────────────────────────────────────────────
// Interface: Tool Center Point solver
// ───────────────────────────────────────────────────────

class ITcpSolver {
public:
    virtual ~ITcpSolver() = default;

    // Solve for the TCP offset given a set of poses collected
    // while the tip is locked in a fixed dimple.
    virtual TcpResult solve(const std::vector<Pose>& poses) = 0;

    // Estimate pivot coverage from poses alone (for live UI feedback).
    virtual double computeCoverage(const std::vector<Pose>& poses) = 0;
};

// ───────────────────────────────────────────────────────
// Pivot method: SVD-based least squares (uses matd from AprilTag)
// ───────────────────────────────────────────────────────

class PivotTcpSolver : public ITcpSolver {
public:
    TcpResult solve(const std::vector<Pose>& poses) override;
    double computeCoverage(const std::vector<Pose>& poses) override;

private:
    // Condition number of the stacked design matrix (via matd SVD)
    static double conditionNumber(const double* A_data, int rows, int cols);

    // Mean reprojection error: how tightly all tip estimates converge
    // NOTE: this measures sample consistency, not absolute accuracy.
    // A systematically wrong offset in the pivot null space produces the
    // same reprojection error as the true offset. conditionNumber is the
    // guard against that failure mode.
    static double reprojectionError(
        const std::vector<Pose>& poses,
        const Vec3& tcp
    );
};

#endif // TCP_SOLVER_H
