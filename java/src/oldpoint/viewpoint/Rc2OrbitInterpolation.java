package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import viewpoint.core.Frame;

/** Interpolates the overhead orbit between snapshot and render frames in nightly Viewpoint. */
public final class Rc2OrbitInterpolation {
    private static final Map<Frame, History> frames = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Orbit> rendered = new ThreadLocal<>();
    private static Orbit last;
    private static Orbit earlier;
    private static Field frameField;

    private Rc2OrbitInterpolation() {}

    public static synchronized boolean capture(boolean overhead, Frame frame) {
        if (!overhead) {
            frames.remove(frame);
            last = null;
            earlier = null;
            return false;
        }
        CameraCapture.Snapshot snapshot = CameraCapture.snapshot(frame);
        float yaw = (float) Math.atan2(-snapshot.directionZ(), -snapshot.directionX());
        float pitch = (float) Math.asin(Math.max(-1.0f, Math.min(1.0f, snapshot.directionY())));
        float distance = (float) Math.sqrt(snapshot.offsetX() * snapshot.offsetX()
                + snapshot.offsetUp() * snapshot.offsetUp() + snapshot.offsetZ() * snapshot.offsetZ());
        Orbit current = new Orbit(yaw, pitch, distance);
        boolean cut = frame.scene.cameraCut || last == null || Math.abs(wrap(current.yaw - last.yaw)) > 0.7f;
        frames.put(frame, new History(current, cut ? current : last, cut || earlier == null ? current : earlier));
        earlier = cut ? current : last;
        last = current;
        return true;
    }

    public static void begin(Frame frame, float fraction) {
        History history = frames.get(frame);
        if (history == null) {
            rendered.remove();
            return;
        }
        rendered.set(blend(history, fraction));
    }

    public static void begin(Object drawer, float fraction) {
        try {
            if (frameField == null) {
                frameField = drawer.getClass().getDeclaredField("frame");
                frameField.setAccessible(true);
            }
            begin((Frame) frameField.get(drawer), fraction);
        } catch (ReflectiveOperationException error) {
            rendered.remove();
            CompatibilityErrors.report(error);
        }
    }

    private static float wrap(float angle) {
        return (float) Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static Orbit blend(History history, float fraction) {
        Orbit from = fraction >= 0.0f ? history.previous : history.earlier;
        Orbit to = fraction >= 0.0f ? history.current : history.previous;
        float amount = Math.max(0.0f, Math.min(1.0f, fraction >= 0.0f ? fraction : 1.0f + fraction));
        return new Orbit(from.yaw + wrap(to.yaw - from.yaw) * amount,
                from.pitch + (to.pitch - from.pitch) * amount,
                from.distance + (to.distance - from.distance) * amount);
    }

    private static void direction(Orbit orbit, float[] result) {
        result[0] = (float) (-Math.cos(orbit.yaw) * Math.cos(orbit.pitch));
        result[1] = (float) Math.sin(orbit.pitch);
        result[2] = (float) (-Math.sin(orbit.yaw) * Math.cos(orbit.pitch));
    }

    public static boolean direction(float[] result) {
        Orbit orbit = rendered.get();
        if (!Bridge.drawTopDown() || orbit == null) {
            return Bridge.direction(result);
        }
        direction(orbit, result);
        return true;
    }

    public static boolean position(Object value, float[] result) {
        Orbit orbit = rendered.get();
        if (!Bridge.frameTopDown(value) || orbit == null) {
            return Bridge.position(value, result);
        }
        Frame frame = (Frame) value;
        direction(orbit, result);
        result[0] = frame.eyeX - result[0] * orbit.distance;
        result[1] = frame.eyeY - result[1] * orbit.distance;
        result[2] = frame.eyeZ - result[2] * orbit.distance;
        return true;
    }

    public static void end() {
        rendered.remove();
    }

    private record History(Orbit current, Orbit previous, Orbit earlier) {}

    private record Orbit(float yaw, float pitch, float distance) {}
}
