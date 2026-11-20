package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.GlProgram;
import static org.lwjgl.opengl.GL20.*;

/** Render-only weather compatibility; climate and gameplay visibility are untouched. */
public final class OverheadWeather {
    private static final String ACTIVE = "uOPWeatherActive", PLAYER = "uOPWeatherPlayer";
    private static final String DATA =
            """
        #ifndef OLDPOINT_WEATHER
        #define OLDPOINT_WEATHER
        uniform float uOPWeatherActive;
        uniform vec3 uOPWeatherPlayer;
        float oldPointAirStart(vec3 eye, vec3 dir) {
          if (uOPWeatherActive < 0.5) return 0.0;
          vec3 toward = uOPWeatherPlayer - eye;
          return max(0.0, dot(toward, toward) / max(dot(toward, dir), 0.0001));
        }
        #endif
        """;
    // True Weathers owns these raw GL programs, outside Viewpoint's GlProgram registry.
    private static int veilProgram, veilLocation = -1, veilPlayer = -1, bankProgram, bankLocation = -1, bankPlayer = -1;
    private static boolean reportedFog, reportedVeil, reportedBank;
    /**
     * Overhead view cuts the ceilings away, but Viewpoint still draws each lamp's air halo, so a ceiling light leaves
     * a glow floating in mid-air. In overhead view an elevated indoor lamp loses its halo when no solid surface lies
     * just below it; floor lamps and TVs have their own model there and keep the halo. The test samples points
     * straight under the lamp, so the result does not change with the camera angle.
     */
    private static final String HALO_SKIP =
            "if (planIndoorsAt(uLightPos[i].xyz) && !planInteriorAt(uLightPos[i].xyz)) continue;";
    private static final String FLOATING_LAMP =
            """
        // oldPointFloatingLamp: overhead view, indoor lamp high above its floor with nothing solid right below it.
        bool oldPointSolidNear(vec3 at) {
          vec4 clip = inverse(uInvViewProjection) * vec4(at, 1.0);
          if (clip.w <= 0.0) return false;
          vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
          if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return false;
          float surface = texture(uDepth, uv).r;
          return surface < 1.0 && distance(scenePos(uv, surface), at) < 0.35;
        }
        bool oldPointFloatingLamp(vec3 lamp) {
          if (uOPWeatherActive < 0.5 || !planIndoorsAt(lamp)) return false;
          if (planHeightAt(lamp) - float(planLevelAt(lamp)) * SQRT6 < 0.6 * SQRT6) return false;
          return !oldPointSolidNear(lamp - vec3(0.0, 0.2, 0.0)) && !oldPointSolidNear(lamp - vec3(0.0, 0.5, 0.0));
        }
        """;

    private OverheadWeather() {}

    public static String library(String path, String source) {
        if (source == null) {
            return null;
        }
        if (path.equals("lib/fog_amount.glsl") && !source.contains(ACTIVE)) {
            return DATA + source;
        }
        if (path.equals("lib/distance.glsl")) {
            String signature = "float distanceShare(vec3 pos, float d, float fogDepth, vec2 fogRange, vec2 haze) {";
            if (!source.contains("d = distance(pos, uOPWeatherPlayer)")) {
                source =
                        source.replace(
                                signature,
                                signature
                                        + """

                  if (uOPWeatherActive > 0.5) {
                    d = distance(pos, uOPWeatherPlayer);
                    fogDepth = d;
                  }
                """);
            }
            if (!reportedFog) {
                reportedFog = true;
                System.out.println("[OldPoint] overhead weather: player-centered fog attached");
            }
        } else if (path.equals("forward.frag")) {
            source =
                    source.replace(
                            "fogAt(vDistance, uFogRange)",
                            "fogAt(uOPWeatherActive > 0.5 ? distance(vPos, uOPWeatherPlayer) : vDistance, uFogRange)");
        } else if (path.equals("far_lamp.vert")) {
            source =
                    source.replace(
                            "float fog = max(fogAt(d, uFogRange), hazeAt(d, uHaze));",
                            "float weatherD = uOPWeatherActive > 0.5 ? distance(centre, uOPWeatherPlayer) : d;\n"
                                    + "  float fog = max(fogAt(weatherD, uFogRange), hazeAt(weatherD, uHaze));");
        } else if (path.equals("lib/lamp_air.glsl")
                && !source.contains("oldPointFloatingLamp")
                && source.contains("vec3 lampAlong(")
                && source.contains("if (planIndoorsAt(lamp)) {")) {
            source =
                    DATA
                            + source.replace("vec3 lampAlong(", FLOATING_LAMP + "\nvec3 lampAlong(")
                                    .replace(
                                            "if (planIndoorsAt(lamp)) {",
                                            "if (oldPointFloatingLamp(lamp)) return vec3(0.0);\n"
                                                    + "  if (planIndoorsAt(lamp)) {");
        } else if (path.equals("volume.frag") && !source.contains(ACTIVE)) {
            source =
                    source.replace("out vec4 fragColor;", DATA + "\nout vec4 fragColor;")
                            .replace(
                                    "vec3 sunLight = airSunLight(dir);",
                                    """
                    float opStart = 0.0;
                    if (uOPWeatherActive > 0.5) {
                      float fullLength = length(ray);
                      opStart = min(oldPointAirStart(uEye, dir), fullLength);
                      len = min(max(fullLength - opStart, 0.0), uFogRange.y);
                    }
                    vec3 sunLight = airSunLight(dir);
                    """)
                            .replace("mix(t0, t1, jitter)", "(opStart + mix(t0, t1, jitter))")
                            .replace("void main() {", FLOATING_LAMP + "\nvoid main() {")
                            .replace(
                                    HALO_SKIP,
                                    HALO_SKIP + "\n    if (oldPointFloatingLamp(uLightPos[i].xyz)) continue;")
                            .replace(
                                    "atan((len + bb) / h) - atan(bb / h)",
                                    "atan((opStart + len + bb) / h) - atan((opStart + bb) / h)");
        }
        return source;
    }

    public static void uniforms(Object value) {
        GlProgram program = (GlProgram) value;
        if (program.has(ACTIVE)) {
            program.set(ACTIVE, CameraDrawing.weatherActive());
        }
        if (program.has(PLAYER)) {
            program.set(
                    PLAYER,
                    CameraDrawing.weatherAnchor(0),
                    CameraDrawing.weatherAnchor(1),
                    CameraDrawing.weatherAnchor(2));
        }
    }

    /** Match only the two audited TW shaders; all other weather/light programs pass through. */
    public static String weatherSource(String source) {
        if (source == null || source.contains(ACTIVE)) {
            return source;
        }
        boolean veil = source.contains("uRainSheets") && source.contains("vec4(col, A)");
        boolean bank = source.contains("float bank(vec3 p)") && source.contains("vec4(uColor * a, a)");
        if (!veil && !bank || !source.contains("out vec4 fragColor;")) {
            return source;
        }
        source = source.replace("out vec4 fragColor;", DATA + "\nout vec4 fragColor;");
        if (veil) {
            // Keep world-anchored media, but integrate only beyond the plane through the player.
            source =
                    source.replace(
                                    "float t1 = min(L, tMax);",
                                    """
                float opStart = oldPointAirStart(uEye, rd);
                t0 = max(t0, opStart);
                float t1 = min(L, tMax + opStart);
                """)
                            .replace(
                                    "fogAt(s)) * openAt(uEye + rd * s)",
                                    "fogAt(uOPWeatherActive > 0.5 ? distance(uEye + rd * s, uOPWeatherPlayer) : s)) * openAt(uEye + rd * s)")
                            .replace(
                                    "float glowSigma(vec3 p, float fogBank) {",
                                    """
                    float glowSigma(vec3 p, float fogBank) {
                      if (uOPWeatherActive > 0.5 && dot(p - uOPWeatherPlayer, uOPWeatherPlayer - uEye) < 0.0) return 0.0;
                    """)
                            .replace(
                                    "float tin = y0 < H ? 1.0 : max(min(tTop, tBot), 1.0), tout = min(max(tTop, tBot), min(L, 300.0));",
                                    "float tin = max(y0 < H ? 1.0 : max(min(tTop, tBot), 1.0), oldPointAirStart(uEye, dir)), tout = min(max(tTop, tBot), min(L, 300.0 + oldPointAirStart(uEye, dir)));");
        } else {
            source =
                    source.replace(
                                    "if (d >= 0.99999 || L > 300.0)",
                                    "if (uOPWeatherActive < 0.5 && (d >= 0.99999 || L > 300.0))")
                            .replace(
                                    "const float H = 2.2, D = 0.045;",
                                    """
                    vec3 opEye = uEye;
                    if (uOPWeatherActive > 0.5) {
                      if (d >= 0.99999) discard;
                      vec3 opDir = ray / max(L, 0.0001);
                      opEye += opDir * min(oldPointAirStart(uEye, opDir), L);
                      ray = p - opEye; L = length(ray);
                    }
                    const float H = 2.2, D = 0.045;
                    """)
                            .replace("float h0 = uEye.y - uGround", "float h0 = opEye.y - uGround")
                            .replace("bank(uEye + ray", "bank(opEye + ray");
        }
        return source;
    }

    public static void linked(String source, int program) {
        if (program <= 0 || source == null || !source.contains(ACTIVE)) {
            return;
        }
        int location = glGetUniformLocation(program, ACTIVE), player = glGetUniformLocation(program, PLAYER);
        if (source.contains("vec4(col, A)")) {
            veilProgram = program;
            veilLocation = location;
            veilPlayer = player;
            if (!reportedVeil) {
                reportedVeil = true;
                System.out.println("[OldPoint] overhead weather: True Weathers foreground medium exclusion attached");
            }
            rawUniform(true);
        } else if (source.contains("vec4(uColor * a, a)")) {
            bankProgram = program;
            bankLocation = location;
            bankPlayer = player;
            if (!reportedBank) {
                reportedBank = true;
                System.out.println("[OldPoint] overhead weather: True Weathers foreground low fog exclusion attached");
            }
            rawUniform(false);
        }
    }

    public static void rawUniform(boolean veil) {
        int program = veil ? veilProgram : bankProgram, location = veil ? veilLocation : bankLocation;
        if (program <= 0 || location < 0) {
            return;
        }
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        if (previous != program) {
            glUseProgram(program);
        }
        glUniform1f(location, CameraDrawing.weatherActive());
        glUniform3f(
                veil ? veilPlayer : bankPlayer,
                CameraDrawing.weatherAnchor(0),
                CameraDrawing.weatherAnchor(1),
                CameraDrawing.weatherAnchor(2));
        if (previous != program) {
            glUseProgram(previous);
        }
    }

    // Optional mod: no linkage to vptw classes, and no changes to its installed files.
    @Patch(className = "vptw.AmbientDraw", methodName = "program", strictMatch = false)
    public static final class SourcePatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(value = 1, readOnly = false) String source) {
            source = OverheadWeather.weatherSource(source);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) String source, @Patch.Return int program) {
            OverheadWeather.linked(source, program);
        }
    }

    // First draw compiles lazily: program() initializes the new program's uniform immediately.
    @Patch(className = "vptw.WeatherVeil", methodName = "draw", strictMatch = false)
    public static final class VeilPatch {
        @Patch.OnEnter
        public static void enter() {
            OverheadWeather.rawUniform(true);
        }
    }

    @Patch(className = "vptw.AmbientDraw", methodName = "draw", strictMatch = false)
    public static final class BankPatch {
        @Patch.OnEnter
        public static void enter() {
            OverheadWeather.rawUniform(false);
        }
    }
}
