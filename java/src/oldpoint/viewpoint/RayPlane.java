package oldpoint.viewpoint;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import viewpoint.platform.GlProgram;
import viewpoint.render.FarRelief;
import viewpoint.render.FrameContext;
import viewpoint.render.MousePick;
import viewpoint.render.Panini;
import zombie.core.SpriteRenderer;

/** Read only a tiny depth neighbourhood on the render thread. No world meshes or native buffers are modified. */
final class RayPlane {
    private static final double STOREY = 2.4494896;
    private static volatile boolean enabled;
    private static volatile Sample sample;
    private static long nextRead;
    private static boolean reported;
    private static Method unwarp;
    private static final FloatBuffer depths = BufferUtils.createFloatBuffer(9);

    record Sample(MousePick.Hit hit, double worldX, double worldUp, double worldZ,
                  RayPlaneMath.Normal normal, long time) {}

    private RayPlane() {}

    static void configure(boolean value) {
        enabled = value;
        sample = null;
    }

    static void clear() {
        sample = null;
    }

    static Sample current() {
        Sample value = sample;
        var ask = MousePick.ask;
        if (!enabled || Pointer.overUI() || value == null || ask == null
                || System.nanoTime() - value.time >= 350_000_000L) {
            return null;
        }
        var old = value.hit.ask();
        return Math.abs(old.x() - ask.x()) <= 3 && Math.abs(old.y() - ask.y()) <= 3
                && old.screenWidth() == ask.screenWidth() && old.screenHeight() == ask.screenHeight() ? value : null;
    }

    static void capture(FrameContext frame, int framebuffer, Matrix4f inverse) {
        if (!enabled || !CameraDrawing.drawTopDown() || MousePick.ask == null) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextRead) {
            return;
        }
        nextRead = now + 100_000_000L;
        sample = null;
        try {
            var ask = MousePick.ask;
            if (ask == null || ask.screenWidth() <= 0 || ask.screenHeight() <= 0) {
                return;
            }
            if (unwarp == null) {
                unwarp = MousePick.class.getDeclaredMethod("unwarped", MousePick.Ask.class, Panini.class);
                unwarp.setAccessible(true);
            }
            var mapped = (MousePick.Ask) unwarp.invoke(null, ask, frame.panini);
            int px = (int) ((long) mapped.x() * frame.width / mapped.screenWidth());
            int py = frame.height - (int) ((long) mapped.y() * frame.height / mapped.screenHeight());
            if (px < 1 || py < 1 || px >= frame.width - 1 || py >= frame.height - 1) {
                return;
            }
            int oldFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
            int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
            int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
            int swapBytes = GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES);
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, 0);
                depths.clear();
                GL11.glReadPixels(px - 1, py - 1, 3, 3, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depths);
            } finally {
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
                GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, swapBytes);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldFbo);
            }
            double[] points = new double[27];
            Vector4f point = new Vector4f();
            for (int i = 0; i < 9; i++) {
                float depth = depths.get(i);
                if (!Float.isFinite(depth) || depth < 0 || depth >= 1) {
                    return;
                }
                float x = 2f * (px + i % 3 - 0.5f) / frame.width - 1;
                float y = 2f * (py + i / 3 - 0.5f) / frame.height - 1;
                inverse.transform(point.set(x, y, depth * 2 - 1, 1));
                if (!Float.isFinite(point.w) || Math.abs(point.w) < 1e-8) {
                    return;
                }
                points[i * 3] = point.x / point.w;
                points[i * 3 + 1] = point.y / point.w;
                points[i * 3 + 2] = point.z / point.w;
            }
            var normal = RayPlaneMath.fit(points, frame.eyeX, frame.eyeY, frame.eyeZ);
            if (normal == null) {
                return;
            }
            double wx = points[12] + frame.scene.originX;
            double wy = points[13] + frame.scene.originY;
            double wz = points[14] + frame.scene.originZ;
            double height = storey(wy, -wx, -wz, frame.scene.relief);
            double dx = points[12] - frame.eyeX, dy = points[13] - frame.eyeY, dz = points[14] - frame.eyeZ;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1e-8) {
                return;
            }
            var hit = new MousePick.Hit(ask, -wx, -wz, height,
                    (float) (-dx / length), (float) (-dz / length), (float) (dy / length));
            sample = new Sample(hit, wx, wy, wz, normal, now);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] ray plane sample unavailable: " + error);
            }
        }
    }

    static double storey(double renderUp, double x, double y, FarRelief.Lift lift) {
        // Match MousePick.collect: undo the terrain displacement at the sampled
        // position, not FarRelief.sink's global terrain baseline.
        double offset = lift == null ? 0 : lift.scene(x, y);
        return (renderUp - offset) / STOREY;
    }

    static void uniforms(GlProgram program, OcclusionSnapshot state, boolean active) {
        if (!program.has("uOPRayPlane")) {
            return;
        }
        Sample value = active ? current() : null;
        program.setInt("uOPRayPlane", value != null ? 1 : 0);
        if (value != null) {
            program.set("uOPRayPoint", (float) (value.worldX - state.originX()),
                    (float) (value.worldUp - state.originY()), (float) (value.worldZ - state.originZ()));
            program.set("uOPRayNormal", (float) value.normal.x(), (float) value.normal.y(), (float) value.normal.z());
        }
    }

    static void draw(Sample value) {
        var hit = value.hit;
        var n = value.normal;
        double up = value.worldUp / STOREY;
        double nx = -n.x(), ny = -n.z(), nz = n.y();
        double tx = Math.abs(nz) < 0.9 ? -ny : 1;
        double ty = Math.abs(nz) < 0.9 ? nx : 0;
        double length = Math.hypot(tx, ty);
        tx /= length;
        ty /= length;
        double bx = -nz * ty, by = nz * tx, bz = nx * ty - ny * tx;
        float[] p = new float[12];
        if (!Pointer.project(hit.x(), hit.y(), up, p, 8)
                || !Pointer.project(hit.x() + nx * 1.25, hit.y() + ny * 1.25,
                        up + nz * 1.25 / STOREY, p, 10)) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double a = (i == 0 || i == 3 ? -0.35 : 0.35);
            double b = (i < 2 ? -0.35 : 0.35);
            if (!Pointer.project(hit.x() + tx * a + bx * b, hit.y() + ty * a + by * b,
                    up + bz * b / STOREY, p, i * 2)) {
                return;
            }
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            segment(p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], 0.1f, 0.4f, 1);
        }
        segment(p[8], p[9], p[10], p[11], 1, 0.9f, 0.1f);
        float dx = p[10] - p[8], dy = p[11] - p[9];
        float size = (float) Math.hypot(dx, dy);
        if (size > 3) {
            dx /= size;
            dy /= size;
            segment(p[10], p[11], p[10] - dx * 9 + dy * 4, p[11] - dy * 9 - dx * 4, 1, 0.9f, 0.1f);
            segment(p[10], p[11], p[10] - dx * 9 - dy * 4, p[11] - dy * 9 + dx * 4, 1, 0.9f, 0.1f);
        }
    }

    private static void segment(float x, float y, float x2, float y2, float r, float g, float b) {
        SpriteRenderer.instance.renderlinef(null, x, y, x2, y2, r, g, b, 1, 2);
    }
}
