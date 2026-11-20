package oldpoint.viewpoint;

import java.lang.reflect.Field;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;

/**
 * Overhead shading of everything outside the player's field of view, applied in the lighting pass so the arc
 * indicator, outlines and UI stay untouched. Darken follows vanilla's dimming; grade desaturates and cools the
 * area like PZ optimisation mods do. The cone is the native viewing angle used for zombie visibility.
 */
public final class VisionShade {
    static final int OFF = 0, DARKEN = 1, GRADE = 2;
    private static final String MODE = "uOPVisionMode";
    private static final String DATA =
            """
        uniform float uOPVisionMode;
        uniform float uOPVisionStrength;
        uniform vec3 uOPVisionCentre;
        uniform vec2 uOPVisionHeading;
        float oldPointOutsideView(vec3 p) {
          vec2 local = -(p - uOPVisionCentre).xz;
          float radius = length(local);
          // Vanilla keeps the squares right around the player in view.
          float near = 1.0 - smoothstep(0.9, 1.7, radius);
          float angle = atan(local.y, local.x) - uOPVisionHeading.x;
          angle = abs(atan(sin(angle), cos(angle)));
          float soft = 0.14;
          float outside = smoothstep(uOPVisionHeading.y - soft, uOPVisionHeading.y + soft, angle);
          if (uOPVisionHeading.y >= 3.1) outside = 0.0;
          return outside * (1.0 - near);
        }
        void main() {
          oldPointLightMain();
          if (uOPVisionMode < 0.5) return;
          float opDepth = texture(uDepth, vUv).r;
          if (opDepth >= 0.9999999) return;
          float o = oldPointOutsideView(scenePos(vUv, opDepth)) * uOPVisionStrength;
          if (o <= 0.0) return;
          vec3 c = fragColor.rgb;
          if (uOPVisionMode < 1.5) {
            c *= 1.0 - 0.65 * o;
          } else {
            float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
            c = mix(c, l * vec3(0.74, 0.83, 1.0) * 0.75, o);
          }
          fragColor.rgb = c;
        }
        """;

    private static volatile int mode = OFF;
    private static volatile float strength = 0.7f;
    private static Field frameField;
    private static boolean failed, reported;

    private VisionShade() {}

    public static void configure(int value, double percent) {
        mode = Math.max(OFF, Math.min(GRADE, value));
        strength = (float) Math.max(0, Math.min(1, percent / 100.0));
    }

    /** Wrap each light pass (base and shader packs) so the shading runs after its own output. */
    public static String shader(String path, String source) {
        if (source == null || !path.equals("light.frag") || source.contains(MODE)) {
            return source;
        }
        int main = source.indexOf("void main()");
        if (main < 0
                || !source.contains("out vec4 fragColor;")
                || !source.contains("scenePos(")
                || !source.contains("uDepth")
                || !source.contains("vUv")) {
            report("light pass layout not recognised; outside-view shading unavailable");
            return source;
        }
        String body = source.substring(main).replaceFirst("void main\\(\\)", "void oldPointLightMain()");
        report("outside-view shading attached");
        return source.substring(0, main) + body + "\n" + DATA;
    }

    public static void uniforms(Object value) {
        GlProgram program = (GlProgram) value;
        if (!program.has(MODE)) {
            return;
        }
        SceneData scene = failed || mode == OFF ? null : scene();
        VisionSystem.Visual v = scene == null ? null : VisionSystem.visual(scene);
        if (v == null) {
            program.set(MODE, 0f);
            return;
        }
        program.set(MODE, (float) mode);
        program.set("uOPVisionStrength", strength);
        program.set(
                "uOPVisionCentre",
                (float) (-v.x() - scene.originX),
                (float) (v.z() * 2.4494896 - scene.originY),
                (float) (-v.y() - scene.originZ));
        program.set("uOPVisionHeading", v.heading(), (float) Math.toRadians(v.degrees()) * 0.5f);
    }

    private static SceneData scene() {
        try {
            FrameContext frame = frame();
            return frame == null ? null : frame.scene;
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            System.out.println("[OldPoint] outside-view shading disabled: " + error);
            return null;
        }
    }

    private static FrameContext frame() throws ReflectiveOperationException {
        if (frameField == null) {
            Field field = WorldRenderer.class.getDeclaredField("frame");
            field.setAccessible(true);
            frameField = field;
        }
        return (FrameContext) frameField.get(null);
    }

    private static void report(String message) {
        if (!reported) {
            reported = true;
            System.out.println("[OldPoint] " + message);
        }
    }
}
