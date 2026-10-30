package oldpoint.viewpoint;

/** Captured roof coverage. A rule identifies either a texture region or a specific packed model. */
record RoofBounds(float x, float y, float up, float x2, float y2, float up2,
                  float r0, float r1, float r2, float r3, int atlas, int model, boolean hidden) {
    RoofBounds(float x, float y, float up, float x2, float y2, float up2,
               float r0, float r1, float r2, float r3, int atlas) {
        this(x, y, up, x2, y2, up2, r0, r1, r2, r3, atlas, -1, true);
    }

    RoofBounds withHidden(boolean value) {
        return new RoofBounds(x, y, up, x2, y2, up2, r0, r1, r2, r3, atlas, model, value);
    }

    static RoofBounds model(RoofSelection.Box box, int model, boolean hidden) {
        return new RoofBounds(box.x(), box.y(), box.up(), box.x2(), box.y2(), box.up2(),
                0, 0, 0, 0, -1, model, hidden);
    }

    /** GPU identity: nonnegative texture atlas, or a negative packed-model index with -1 reserved. */
    int identity() {
        return model >= 0 ? -model - 2 : atlas;
    }
}
