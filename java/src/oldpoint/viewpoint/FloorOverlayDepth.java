package oldpoint.viewpoint;

import java.lang.reflect.Field;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.VertexFormat;
import zombie.core.properties.IsoPropertyType;
import zombie.iso.sprite.IsoSprite;

/** Keep packed rugs above the floor art when the models share the same plane. */
public final class FloorOverlayDepth {
    private static volatile Field recipeFlags;

    private FloorOverlayDepth() {}

    public static void markRug(IsoSprite sprite, Object placement) {
        if (sprite == null || placement == null
                || !"FloorRug".equals(sprite.getProperty(IsoPropertyType.MOVE_TYPE))) {
            return;
        }
        try {
            // Recipe.Op is package-private. Change the new placement before Viewpoint packs its vertices.
            Field flags = recipeFlags;
            if (flags == null) {
                flags = placement.getClass().getDeclaredField("flags");
                flags.setAccessible(true);
                recipeFlags = flags;
            }
            flags.setInt(placement, flags.getInt(placement) | VertexFormat.DECAL);
        } catch (ReflectiveOperationException error) {
            CompatibilityErrors.report(error);
        }
    }

    @Patch(className = "viewpoint.world.PackGather", methodName = "place", strictMatch = false)
    public static final class PackPlacementPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(2) IsoSprite sprite, @Patch.Return Object placement) {
            FloorOverlayDepth.markRug(sprite, placement);
        }
    }
}
