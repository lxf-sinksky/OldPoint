package oldpoint.viewpoint;

/** Fit a visible plane to a 3x3 world-space depth neighbourhood; reject silhouettes and mixed surfaces. */
final class RayPlaneMath {
    record Normal(double x, double y, double z) {}

    private RayPlaneMath() {}

    static Normal fit(double[] p, double eyeX, double eyeY, double eyeZ) {
        if (p.length != 27) {
            return null;
        }
        for (double value : p) {
            if (!Double.isFinite(value)) {
                return null;
            }
        }
        double ax = p[15] - p[9], ay = p[16] - p[10], az = p[17] - p[11];
        double bx = p[21] - p[3], by = p[22] - p[4], bz = p[23] - p[5];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!Double.isFinite(length) || length < 1e-10) {
            return null;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        double extent = 0;
        for (int i = 0; i < p.length; i += 3) {
            double dx = p[i] - p[12], dy = p[i + 1] - p[13], dz = p[i + 2] - p[14];
            extent = Math.max(extent, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        if (extent < 1e-6 || extent > 10) {
            return null;
        }
        double tolerance = 0.015 + extent * 0.04;
        for (int i = 0; i < p.length; i += 3) {
            double distance = nx * (p[i] - p[12]) + ny * (p[i + 1] - p[13]) + nz * (p[i + 2] - p[14]);
            if (Math.abs(distance) > tolerance) {
                return null;
            }
        }
        if (nx * (eyeX - p[12]) + ny * (eyeY - p[13]) + nz * (eyeZ - p[14]) < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return new Normal(nx, ny, nz);
    }
}
