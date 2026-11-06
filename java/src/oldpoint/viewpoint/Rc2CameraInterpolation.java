package oldpoint.viewpoint;

import java.lang.reflect.Field;
import org.joml.Matrix4f;
import viewpoint.core.Frame;

/** Keeps the nightly camera pivot and interpolated view matrix aligned with the overhead camera. */
public final class Rc2CameraInterpolation {
    private static Field eyeField;
    private static Field directionField;
    private static Field viewField;

    private Rc2CameraInterpolation() {}

    public static void capture(Frame frame) {
        frame.cameraShot.pivotX = frame.eyeX;
        frame.cameraShot.pivotY = frame.eyeY;
        frame.cameraShot.pivotZ = frame.eyeZ;
    }

    public static boolean centerDrawing(Object drawer) {
        if (!Bridge.drawTopDown()) {
            return false;
        }
        try {
            if (eyeField == null) {
                Class<?> type = drawer.getClass();
                Field eye = type.getDeclaredField("eye");
                Field direction = type.getDeclaredField("dir");
                Field view = type.getDeclaredField("view");
                eye.setAccessible(true);
                direction.setAccessible(true);
                view.setAccessible(true);
                eyeField = eye;
                directionField = direction;
                viewField = view;
            }
            float[] eye = (float[]) eyeField.get(drawer);
            float[] direction = (float[]) directionField.get(drawer);
            if (!Rc2OrbitInterpolation.direction(direction)) {
                return false;
            }
            ((Matrix4f) viewField.get(null)).setLookAt(
                    eye[0], eye[1], eye[2],
                    eye[0] + direction[0], eye[1] + direction[1], eye[2] + direction[2],
                    0.0f, 1.0f, 0.0f);
            return true;
        } catch (ReflectiveOperationException error) {
            CompatibilityErrors.report(error);
            return false;
        }
    }
}
