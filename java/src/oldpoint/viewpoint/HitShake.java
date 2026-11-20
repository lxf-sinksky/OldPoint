package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;
import zombie.iso.objects.ObjectRenderEffects;

/**
 * Vanilla shakes an object's 3D model by its render effect (a zombie hitting a door) when it draws the model:
 * IsoObject.renderModel adds effect.x1/y1 * 1.5 to the model position. Viewpoint captures object models at the bare
 * square centre, so its doors never rattle. Apply the same offset to the captured placement.
 */
public final class HitShake {
    private static final float SCALE = 1.5f;
    private static final ThreadLocal<IsoObject> capturing = new ThreadLocal<>();

    private HitShake() {}

    public static void begin(IsoObject object) {
        if (object == null) {
            capturing.remove();
        } else {
            capturing.set(object);
        }
    }

    public static void end() {
        capturing.remove();
    }

    public static float x(float original) {
        ObjectRenderEffects effect = effect();
        return effect == null ? original : original + (float) effect.x1 * SCALE;
    }

    public static float y(float original) {
        ObjectRenderEffects effect = effect();
        return effect == null ? original : original + (float) effect.y1 * SCALE;
    }

    private static ObjectRenderEffects effect() {
        IsoObject object = capturing.get();
        if (object == null) {
            return null;
        }
        try {
            return object.getObjectRenderEffectsToApply();
        } catch (RuntimeException error) {
            return null;
        }
    }

    @Patch(className = "viewpoint.models.RigidCapture", methodName = "object", strictMatch = false)
    public static final class ObjectPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(3) IsoObject object) {
            HitShake.begin(object);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            HitShake.end();
        }
    }

    @Patch(className = "viewpoint.models.RigidCapture", methodName = "placed", strictMatch = false)
    public static final class PlacedPatch {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(value = 1, readOnly = false) float x,
                @Patch.Argument(value = 2, readOnly = false) float y) {
            x = HitShake.x(x);
            y = HitShake.y(y);
        }
    }
}
