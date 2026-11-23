package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

/** Stop Viewpoint cutting characters at walls, windows, doors and fences while drawing the overhead view. */
public final class OverheadWallClip {
    private static volatile boolean reported;

    private OverheadWallClip() {}

    /**
     * Viewpoint discards model fragments past every blocked edge of the character's square, so a body pressed
     * against a wall, window, door or fence loses the parts that lean over it. From Viewpoint's own camera the wall
     * hides that side; from overhead, and through OldPoint's cutaway, it is in plain view. Drawing the whole body
     * looks right far more often than a limb poking a few centimetres through a wall looks wrong.
     */
    public static int closed(int closed) {
        // Shadow and model passes run outside CameraDrawing's draw state, so key off the camera itself.
        if (closed == 0 || !CameraController.topDownActive()) {
            return closed;
        }
        if (!reported) {
            reported = true;
            System.out.println("[OldPoint] OverheadWallClip: wall clip off in top-down drawing");
        }
        return 0;
    }

    // Advice is inlined into Viewpoint's package-private WallClip. strictMatch selects the
    // four-argument closed(IsoCell, float, float, float) overload; the two-square overload has two.
    @Patch(className = "viewpoint.models.WallClip", methodName = "closed", strictMatch = true)
    public static final class ClosedPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Object cell,
                @Patch.Argument(1) float x,
                @Patch.Argument(2) float y,
                @Patch.Argument(3) float z,
                @Patch.Return(readOnly = false) int result) {
            result = OverheadWallClip.closed(result);
        }
    }
}
