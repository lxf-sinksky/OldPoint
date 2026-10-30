package oldpoint.viewpoint;

import viewpoint.render.FarRelief;

/** Use the real terrain warp as the oracle: a third-storey surface must not be reported dozens of levels below it. */
public final class RayHeightTest {
    public static void main(String[] args) throws Exception {
        float[] heights = {20, 20, 20, 20};
        var grid = FarRelief.Field.of(2, 2, heights, heights);
        var lift = new FarRelief.Lift(grid, 2, 20, 8228, 11596);
        double x = 8227.681, y = 11595.783;
        double rendered = 3 * 2.4494896 + lift.scene(x, y);
        double recovered = RayPlane.storey(rendered, x, y, lift);
        System.out.println("Third-storey roof: actual=" + recovered + ", expected=3.0");
        if (Math.abs(recovered - 3) > 0.001) {
            throw new AssertionError("Ray debug height does not undo the native point-specific terrain warp");
        }
        if (Math.abs(RayPlane.storey(3 * 2.4494896, x, y, null) - 3) > 0.001) {
            throw new AssertionError("Unwarped surface height changed");
        }
        float[] varied = {5, 30, 10, 40};
        var variable = new FarRelief.Lift(FarRelief.Field.of(2, 2, varied, varied), 2, 20, 0, 0);
        for (double[] point : new double[][] {{0.25, 0.25}, {1.25, 0.5}, {0.5, 1.25}}) {
            double drawn = 2 * 2.4494896 + variable.scene(point[0], point[1]);
            if (Math.abs(RayPlane.storey(drawn, point[0], point[1], variable) - 2) > 0.001) {
                throw new AssertionError("Point-specific terrain displacement not removed");
            }
        }
        System.out.println("PASS native terrain inversion, no-relief height and variable terrain samples");
    }
}
