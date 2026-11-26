package oldpoint.viewpoint;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;
import java.lang.reflect.Field;
import viewpoint.render.FrameContext;
import viewpoint.render.Targets;
import viewpoint.render.WorldRenderer;

/** Optional screen-space tilt-shift blur, applied to the overhead world before Viewpoint draws its crosshair. */
final class TiltShift {
    private static volatile boolean enabled;
    private static volatile int focus, band, upper, lower, curve;
    private static volatile boolean follow;
    private static final float[] point = new float[2];
    private static boolean failed;
    private static boolean reportedSetting, reportedSkipped, reportedRendered;
    private static GLCapabilities context;
    private static int program, vao, texelLocation, bandLocation, radiiLocation, curveLocation;
    private static Field outputField, viewportField;
    private static Field targetsField, irisField;
    private static boolean depthUnavailable;
    private static int depthLocation, projectionLocation;
    private static int copyFbo, copyTexture, width, height;

    private TiltShift() {}

    static void configure(boolean value, int center, int clearHeight, int top, int bottom, int transition, boolean track) {
        focus = center;
        band = clearHeight;
        upper = top;
        lower = bottom;
        curve = transition;
        follow = track;
        if (!reportedSetting || enabled != value) {
            System.out.println("[OldPoint] tilt-shift setting=" + value);
            reportedSetting = true;
        }
        enabled = value;
        if (!value) {
            failed = false;
        }
    }

    static void draw(FrameContext frame) {
        boolean active = enabled;
        if (!active && copyFbo == 0) {
            return;
        }
        GLCapabilities current = GL.getCapabilities();
        if (context != current) {
            // OpenGL names from a previous context must not be deleted in a new context.
            context = current;
            copyFbo = copyTexture = width = height = 0;
            program = vao = 0;
        }
        if (!active) {
            release();
            return;
        }
        if (failed) {
            return;
        }
        if (!CameraDrawing.drawTopDown()) {
            if (!reportedSkipped) {
                System.out.println("[OldPoint] tilt-shift skipped: camera is not overhead");
                reportedSkipped = true;
            }
            return;
        }
        try {
            if (outputField == null) {
                viewportField = FrameContext.class.getDeclaredField("viewport");
                viewportField.setAccessible(true);
                outputField = FrameContext.class.getDeclaredField("outputDrawFbo");
                outputField.setAccessible(true);
            }
            int[] screenArea = (int[]) viewportField.get(frame);
            int sceneFbo = outputField.getInt(frame);
            if (screenArea[2] <= 0 || screenArea[3] <= 0) {
                return;
            }
            int sceneDepth = sceneDepth(frame);
            render(sceneFbo, screenArea, sceneDepth, frame.jitteredProjection.m22(), frame.jitteredProjection.m32());
            if (!reportedRendered) {
                System.out.println("[OldPoint] tilt-shift at scene composite, framebuffer=" + sceneFbo + ", " + width + "x" + height
                        + ", clear band=" + band + "%, radii=" + upper + "/" + lower + ", disk samples=24, depth=" + sceneDepth);
                reportedRendered = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            System.out.println("[OldPoint] tilt-shift disabled: " + error);
            error.printStackTrace(System.out);
            release();
        }
    }

    private static int sceneDepth(FrameContext frame) {
        // Panini warps the final colour, and Iris supplies its own depth target.
        if (depthUnavailable || (frame.panini != null && frame.panini.on())) return 0;
        try {
            if (targetsField == null) {
                irisField = WorldRenderer.class.getDeclaredField("irisDrawn");
                irisField.setAccessible(true);
                targetsField = WorldRenderer.class.getDeclaredField("targets");
                targetsField.setAccessible(true);
            }
            if (irisField.getBoolean(null)) return 0;
            Targets targets = (Targets) targetsField.get(null);
            return targets == null ? 0 : targets.depth;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            depthUnavailable = true;
            System.out.println("[OldPoint] tilt-shift using colour-only blur: " + error);
            return 0;
        }
    }

    private static void render(int sceneFbo, int[] area, int sceneDepth, float projection22, float projection32) {
        int previousReadFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = glGetInteger(GL_READ_BUFFER);
        int previousDrawBuffer = glGetInteger(GL_DRAW_BUFFER);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int[] previousViewport = new int[4], previousScissor = new int[4];
        glGetIntegerv(GL_VIEWPORT, previousViewport);
        glGetIntegerv(GL_SCISSOR_BOX, previousScissor);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean blend = glIsEnabled(GL_BLEND);
        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_ENABLE_BIT);
        glActiveTexture(GL_TEXTURE1);
        int previousDepthTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousDepthSampler = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousSampler = glGetInteger(GL_SAMPLER_BINDING);
        try {
            ensure(area[2], area[3]);
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sceneFbo);
            glReadBuffer(sceneFbo == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glBlitFramebuffer(area[0], area[1], area[0] + width, area[1] + height,
                    0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);

            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, sceneFbo);
            glDrawBuffer(sceneFbo == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0);
            glColorMask(true, true, true, false);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_ALPHA_TEST);
            glViewport(area[0], area[1], width, height);
            glDisable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDepthMask(false);
            glUseProgram(program);
            glUniform2f(texelLocation, 1.0f / width, 1.0f / height);
            float center = 1f - focus / 100f;
            var player = follow ? zombie.characters.IsoPlayer.getInstance() : null;
            if (follow && player != null && Pointer.project(player.getX(), player.getY(),
                    Pointer.groundHeight(player.getZ()) + 0.35, point, 0)) {
                center = Math.max(0f, Math.min(1f, 1f - point[1] / zombie.core.Core.getInstance().getScreenHeight()));
            }
            glUniform2f(bandLocation, center, band / 200f);
            glUniform2f(radiiLocation, (float) upper, (float) lower);
            glUniform1f(curveLocation, (float) curve);
            glUniform1i(depthLocation, 1);
            glUniform2f(projectionLocation, projection22, sceneDepth == 0 ? 0f : projection32);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, sceneDepth);
            glBindSampler(1, 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, copyTexture);
            glBindSampler(0, 0);
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            if (!reportedRendered && glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING) == 0
                    && glGetInteger(GL_PACK_SKIP_ROWS) == 0 && glGetInteger(GL_PACK_SKIP_PIXELS) == 0
                    && glGetInteger(GL_PACK_ROW_LENGTH) == 0) {
                // One readback on the first active frame, never in the steady-state path.
                int x = width / 2 - Math.min(width, 64) / 2;
                int y = height / 16;
                int count = Math.min(width, 64);
                java.nio.ByteBuffer before = org.lwjgl.BufferUtils.createByteBuffer(count * 4);
                java.nio.ByteBuffer after = org.lwjgl.BufferUtils.createByteBuffer(count * 4);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, copyFbo);
                glReadBuffer(GL_COLOR_ATTACHMENT0);
                glReadPixels(x, y, count, 1, GL_RGBA, GL_UNSIGNED_BYTE, before);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, sceneFbo);
                glReadBuffer(sceneFbo == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0);
                glReadPixels(area[0] + x, area[1] + y, count, 1, GL_RGBA, GL_UNSIGNED_BYTE, after);
                int changed = 0;
                for (int i = 0; i < count; i++) {
                    if ((before.getInt(i * 4) >>> 8) != (after.getInt(i * 4) >>> 8)) {
                        changed++;
                    }
                }
                System.out.println("[OldPoint] tilt-shift lower-edge probe: " + changed + "/" + count
                        + " pixels changed");
            }
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFbo);
            glReadBuffer(previousReadBuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFbo);
            glDrawBuffer(previousDrawBuffer);
            glPopAttrib();
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3]);
            if (scissor) {
                glEnable(GL_SCISSOR_TEST);
            } else {
                glDisable(GL_SCISSOR_TEST);
            }
            if (blend) {
                glEnable(GL_BLEND);
            } else {
                glDisable(GL_BLEND);
            }
            if (depth) {
                glEnable(GL_DEPTH_TEST);
            } else {
                glDisable(GL_DEPTH_TEST);
            }
            if (cull) {
                glEnable(GL_CULL_FACE);
            } else {
                glDisable(GL_CULL_FACE);
            }
            glDepthMask(depthWrite);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, previousDepthTexture);
            glBindSampler(1, previousDepthSampler);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glBindSampler(0, previousSampler);
            glActiveTexture(previousActive);
            glUseProgram(previousProgram);
            glBindVertexArray(previousVao);

        }
    }

    private static void ensure(int w, int h) {
        if (program == 0) {
            int linked = glCreateProgram();
            try {
                attach(linked, GL_VERTEX_SHADER, VERTEX);
                attach(linked, GL_FRAGMENT_SHADER, FRAGMENT);
                glLinkProgram(linked);
                if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE) {
                    throw new IllegalStateException(glGetProgramInfoLog(linked));
                }
                texelLocation = glGetUniformLocation(linked, "uTexel");
                bandLocation = glGetUniformLocation(linked, "uBand");
                radiiLocation = glGetUniformLocation(linked, "uRadii");
                curveLocation = glGetUniformLocation(linked, "uLinear");
                depthLocation = glGetUniformLocation(linked, "uDepth");
                projectionLocation = glGetUniformLocation(linked, "uProjection");
                vao = glGenVertexArrays();
                program = linked;
            } finally {
                if (program == 0) {
                    glDeleteProgram(linked);
                }
            }
        }
        if (copyFbo != 0 && width == w && height == h) {
            return;
        }
        int max = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (w > max || h > max) {
            throw new IllegalStateException("Invalid tilt-shift dimensions: " + w + "x" + h);
        }
        int nextTexture = 0, nextFbo = 0;
        try {
            nextTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, nextTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 6);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            nextFbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, nextFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, nextTexture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if (nextTexture == 0 || nextFbo == 0
                    || glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete tilt-shift framebuffer");
            }
            release();
            copyTexture = nextTexture;
            copyFbo = nextFbo;
            width = w;
            height = h;
            nextTexture = nextFbo = 0;
        } finally {
            if (nextFbo != 0) {
                glDeleteFramebuffers(nextFbo);
            }
            if (nextTexture != 0) {
                glDeleteTextures(nextTexture);
            }
        }
    }

    private static void release() {
        if (copyFbo != 0) {
            glDeleteFramebuffers(copyFbo);
        }
        if (copyTexture != 0) {
            glDeleteTextures(copyTexture);
        }
        copyFbo = copyTexture = width = height = 0;
    }

    private static void attach(int program, int type, String source) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IllegalStateException(glGetShaderInfoLog(shader));
            }
            glAttachShader(program, shader);
        } finally {
            glDeleteShader(shader);
        }
    }

    private static final String VERTEX = """
            #version 330
            out vec2 vUv;
            void main() {
              vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
              vUv = p;
              gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330
            in vec2 vUv;
            uniform sampler2D uScene;
            uniform sampler2D uDepth;
            uniform vec2 uProjection;
            uniform vec2 uTexel;
            uniform vec2 uBand, uRadii;
            uniform float uLinear;
            out vec4 fragColor;
            float blurRadius(float y) {
              float dy = y - uBand.x;
              float edge = dy > 0.0 ? 1.0 - uBand.x : uBand.x;
              float amount = clamp((abs(dy) - uBand.y) / max(edge - uBand.y, 0.00001), 0.0, 1.0);
              if (uLinear < 0.5) amount = smoothstep(0.0, 1.0, amount);
              return amount * (dy > 0.0 ? uRadii.x : uRadii.y);
            }
            float viewDepth(vec2 uv) {
              ivec2 size = textureSize(uDepth, 0);
              float z = texelFetch(uDepth, clamp(ivec2(uv * size), ivec2(0), size - 1), 0).r;
              return uProjection.y / (z * 2.0 - 1.0 + uProjection.x);
            }
            void main() {
              float radius = blurRadius(vUv.y);
              if (radius < 0.01) {
                fragColor = textureLod(uScene, vUv, 0.0);
                return;
              }
              bool hasDepth = uProjection.y != 0.0;
              float centerDepth = hasDepth ? viewDepth(vUv) : 0.0;
              float lod = log2(max(radius * 0.22, 1.0));
              // Keep the central sample sharp so rejected neighbours cannot tint a silhouette.
              float total = 1.0 / max(1.0, radius * 0.25);
              vec4 sum = total * textureLod(uScene, vUv, 0.0);
              for (int i = 0; i < 24; ++i) {
                float ring = sqrt((float(i) + 0.5) / 24.0);
                float angle = float(i) * 2.39996323;
                vec2 uv = clamp(vUv + vec2(cos(angle), sin(angle)) * ring * radius * uTexel, 0.0, 1.0);
                float weight = (1.0 - 0.65 * ring * ring)
                             * smoothstep(-0.5, 0.5, blurRadius(uv.y) - ring * radius);
                if (hasDepth) {
                  float tolerance = 0.5 + 0.01 * centerDepth;
                  weight *= 1.0 - smoothstep(tolerance, 3.0 * tolerance, abs(viewDepth(uv) - centerDepth));
                }
                sum += weight * textureLod(uScene, uv, lod);
                total += weight;
              }
              fragColor = sum / total;
            }
            """;
}
