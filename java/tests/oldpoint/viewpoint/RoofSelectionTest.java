package oldpoint.viewpoint;

import java.util.List;

/** Whole-building roof selection; individual roof positions must not introduce visibility holes. */
public final class RoofSelectionTest {
    private static void check(String name, int actual, int expected) {
        if (actual != expected) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        var regions = List.of(new Layout.Rect(0, 0, 10, 10));
        var front = new RoofSelection.Box(1, 1, 3, 2, 2, 6);
        var back = new RoofSelection.Box(8, 8, 3, 9, 9, 6);
        check("selected building front roof", RoofSelection.reason(front, 0, true, regions), RoofSelection.COVER);
        check("selected building back roof also hidden", RoofSelection.reason(back, 0, true, regions),
                RoofSelection.COVER);
        check("selected owner controls overhanging roof outside footprint", RoofSelection.reason(
                new RoofSelection.Box(-3, -3, 3, -2, -2, 6), 0, true, regions), RoofSelection.COVER);
        check("known neighbouring owner does not inherit overlapping selection", RoofSelection.reason(
                front, 0, false, regions), RoofSelection.KEEP);
        check("unassigned eave uses one tile fallback", RoofSelection.reason(
                new RoofSelection.Box(-0.8f, 1, 3, -0.2f, 2, 6), 0, null, regions), RoofSelection.COVER);
        check("distant unassigned roof kept", RoofSelection.reason(
                new RoofSelection.Box(-4, 1, 3, -2, 2, 6), 0, null, regions), RoofSelection.KEEP);
        check("below walking floor kept even for selected owner", RoofSelection.reason(
                new RoofSelection.Box(1, 1, -1, 2, 2, 0), 0, true, regions), RoofSelection.BELOW);
        check("missing bounds not guessed at walking level", RoofSelection.reason(null, 0, true, regions),
                RoofSelection.UNKNOWN);
        check("invalid bounds retained", RoofSelection.reason(
                new RoofSelection.Box(Float.NaN, 0, 1, 1, 1, 2), 0, true, regions), RoofSelection.UNKNOWN);
        check("empty building selection retains roofs", RoofSelection.reason(front, 0, false, List.of()),
                RoofSelection.KEEP);
        check("upper storey selected roof", RoofSelection.reason(
                new RoofSelection.Box(101, 201, 8, 102, 202, 10), 2, true, List.of()), RoofSelection.COVER);
        check("lower storey roof protected after climbing", RoofSelection.reason(front, 3, true, regions),
                RoofSelection.BELOW);
    }
}
