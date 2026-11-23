package oldpoint.viewpoint;

import java.lang.reflect.Field;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.GlProgram;

/** Keep characters whole in overhead view; every other camera clear rule stays as it was. */
public final class OverheadCameraClear {
    private static volatile Field name;
    private static volatile boolean reported;

    private OverheadCameraClear() {}

    /**
     * Viewpoint dithers away fragments inside a cylinder from the eye to the player's chest. From overhead that
     * cylinder cuts the upper bodies of characters standing between the camera and the player. Only Viewpoint's
     * character programs ("model" and "model glass") have the clear switched off; vehicles and the world pass keep
     * their existing behaviour.
     */
    public static void apply(Object value) {
        if (!(value instanceof GlProgram program) || !CameraController.topDownActive()) {
            return;
        }
        String kind = name(program);
        if (!"model".equals(kind) && !"model glass".equals(kind)) {
            return;
        }
        if (!program.has("uClearEye")) {
            return;
        }
        program.set("uClearEye", 0.0f, 0.0f, 0.0f, 0.0f);
        if (!reported) {
            reported = true;
            System.out.println("[OldPoint] OverheadCameraClear: character camera clear off in top-down view");
        }
    }

    private static String name(GlProgram program) {
        try {
            Field field = name;
            if (field == null) {
                field = GlProgram.class.getDeclaredField("name");
                field.setAccessible(true);
                name = field;
            }
            return (String) field.get(program);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // CameraClear.apply(GlProgram) is package-private; advice is inlined there and calls this public bridge.
    @Patch(className = "viewpoint.render.CameraClear", methodName = "apply", strictMatch = false)
    public static final class ApplyPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object program) {
            OverheadCameraClear.apply(program);
        }
    }
}
