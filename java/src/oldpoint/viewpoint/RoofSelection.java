package oldpoint.viewpoint;

import java.util.List;

/** Roof identity is supplied by capture; this module only decides which proven roofs should move out of view. */
final class RoofSelection {
    static final int KEEP = 0, COVER = 1, BELOW = 2, UNKNOWN = 3;

    record Box(float x, float y, float up, float x2, float y2, float up2) {
        boolean valid() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(up)
                    && Float.isFinite(x2) && Float.isFinite(y2) && Float.isFinite(up2)
                    && x < x2 && y < y2 && up <= up2;
        }


    }

    private RoofSelection() {}

    static int reason(Box box, int floor, Boolean selectedOwner, List<Layout.Rect> regions) {
        if (box == null || !box.valid()) {
            return UNKNOWN;
        }
        if (box.up2 <= floor * CutawayProjection.STOREY + 0.05) {
            return BELOW;
        }
        // A known owner is authoritative: a neighbouring building's eave
        // must not inherit another building's selection merely by overlapping it.
        if (selectedOwner != null) {
            return selectedOwner ? COVER : KEEP;
        }
        // Only unassigned pieces use a one-tile eave margin as a fallback.
        for (var r : regions) {
            if (box.x2 > r.x() - 1 && box.y2 > r.y() - 1
                    && box.x < r.x() + r.width() + 1 && box.y < r.y() + r.height() + 1) {
                return COVER;
            }
        }
        return KEEP;
    }
}
