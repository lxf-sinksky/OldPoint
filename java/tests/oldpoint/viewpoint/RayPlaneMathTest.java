package oldpoint.viewpoint;

/** Known planes must recover their direction; a depth edge must not pretend to be one continuous surface. */
public final class RayPlaneMathTest {
    private static double[] slope(double a, double b) {
        double[] points = new double[27];
        for (int i = 0; i < 9; i++) {
            double x = (i % 3 - 1) * 0.2, z = (i / 3 - 1) * 0.2;
            points[i * 3] = x;
            points[i * 3 + 1] = 1 + a * x + b * z;
            points[i * 3 + 2] = z;
        }
        return points;
    }

    private static void direction(String name, RayPlaneMath.Normal n, double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (n == null || (n.x() * x + n.y() * y + n.z() * z) / length < 0.99999) {
            throw new AssertionError(name + ": " + n);
        }
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        direction("flat roof normal", RayPlaneMath.fit(slope(0, 0), 0, 10, 0), 0, 1, 0);
        direction("inclined roof normal", RayPlaneMath.fit(slope(0.5, -0.75), 0, 10, 0), -0.5, 1, 0.75);
        direction("normal faces the viewer below", RayPlaneMath.fit(slope(0, 0), 0, -10, 0), 0, -1, 0);
        double[] wall = slope(0, 0);
        for (int i = 0; i < 9; i++) {
            wall[i * 3] = 2;
            wall[i * 3 + 1] = (i % 3 - 1) * 0.2;
        }
        direction("vertical wall normal", RayPlaneMath.fit(wall, 10, 0, 0), 1, 0, 0);
        double[] edge = slope(0, 0);
        edge[13] += 1;
        if (RayPlaneMath.fit(edge, 0, 10, 0) != null) {
            throw new AssertionError("Depth discontinuity produced a misleading normal");
        }
        double[] invalid = slope(0, 0);
        invalid[0] = Double.NaN;
        if (RayPlaneMath.fit(invalid, 0, 10, 0) != null
                || RayPlaneMath.fit(new double[27], 0, 10, 0) != null) {
            throw new AssertionError("Invalid or degenerate samples accepted");
        }
        System.out.println("PASS depth discontinuity, invalid and degenerate samples rejected");
    }
}
