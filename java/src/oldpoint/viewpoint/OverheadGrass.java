package oldpoint.viewpoint;

import static org.lwjgl.opengl.GL20.glGetUniformi;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Vector3f;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;

/** Grows the nearest grass profile across the overhead view, using Viewpoint's existing buffers. */
public final class OverheadGrass {
    private static final String ACTIVE = "uOPOverheadGrass";
    private static final String STRIDE = "uOPGrassTileStride";
    private static final String TILE_QUEUE = "uOPGrassTileQueue";
    private static final String MAIN = "void main() {";
    private static final String FINISH = "if (i == 0u) finish();";
    private static final String CANDIDATE = """
            uint per = uint(uSlots), squareIndex = i / per, k = i - squareIndex * per;
              if (squareIndex >= uint(uSquaresX * uSquaresY)) return;""";
    private static final String NEAR_LIGHT = "uOPGrassNearLight";
    private static final String BAND_DENSITY =
            "float away = length(place - uEyeWorld), share = shareAt(away), u = (float(k) + 0.5) / float(uBlades);";
    private static final String BAND_TEST = "if (away < uInner || away >= uOuter || u >= share) return;";
    private static final String CAMERA_DISTANCE = "float fromEye = distance(foot, uEye);";
    private static final String CAMERA_DIRECTION = "vec2 toEye = uEye.xz - foot.xz;";
    private static final String TIER_TEST = "int tier = fromEye < uLodReach";
    private static final String WIDTH_SCALE = "min(pow(1.0 / share, WIDER_POWER), WIDER_MOST)";
    private static final String RANGE_FADE = "smoothstep(float(uReach), 0.85 * float(uReach), away)";
    private static final String PIXEL_FADE = """
            height *= smoothstep(PIXELS_NONE, PIXELS_FULL,
                  abs(uViewProjection[1].y) * height / max(clip.w, 0.001) * 0.5 * uScreenHeight);""";
    private static final String NORMAL_BLEND =
            "mix(NEAR_GROUND, FAR_GROUND, smoothstep(GROUND_FROM, GROUND_TO, distance(foot, uEye)))";

    private static final int MAP_SIDE = 256;
    private static final int COMPUTE_GROUP_SIZE = 64;
    private static final int DRAW_GROUP_SIZE = 16;
    private static final int VEC4S_PER_BLADE = 4;
    // Viewpoint's longest blade is 0.8 units, with a height scale of up to 3.
    private static final int BLADE_MARGIN = 3;
    private static final ThreadLocal<Draw> current = new ThreadLocal<>();
    private static boolean reportedActive;

    private OverheadGrass() {}

    public static String shader(String path, String source) {
        if (source == null) {
            return null;
        }
        if ("grass.vert".equals(path) && !source.contains(NEAR_LIGHT)
                && source.contains("uniform int uSegments;") && source.contains(NORMAL_BLEND)) {
            return source.replace("uniform int uSegments;", "uniform int uSegments;\nuniform int " + NEAR_LIGHT + ";")
                    .replace(NORMAL_BLEND, NEAR_LIGHT + " == 1 ? NEAR_GROUND : " + NORMAL_BLEND);
        }
        if (!"grass_blades.comp".equals(path) || source.contains(ACTIVE)) {
            return source;
        }
        String shader = source.replace("\r\n", "\n");
        if (!shader.contains("uniform int uFinish;") || !shader.contains(BAND_DENSITY)
                || !shader.contains(BAND_TEST) || !shader.contains(TIER_TEST) || !shader.contains(PIXEL_FADE)
                || !shader.contains(MAIN) || !shader.contains(FINISH) || !shader.contains(CANDIDATE)
                || !shader.contains(WIDTH_SCALE) || !shader.contains(RANGE_FADE)
                || !shader.contains(CAMERA_DISTANCE) || !shader.contains(CAMERA_DIRECTION)) {
            return source;
        }
        return shader.replace("uniform int uFinish;",
                        "uniform int " + ACTIVE + ", " + STRIDE + ", " + TILE_QUEUE + ";\nuniform int uFinish;")
                .replace(MAIN, """
                        void oldPointQueueGrassTile(uint index) {
                          if (index >= uint(uSquaresX * uSquaresY)) return;
                          ivec2 square = ivec2(uCorner)
                              + ivec2(int(index) % uSquaresX, int(index) / uSquaresX);
                          uint value = valueAt(square);
                          bool grass = (value & 0xFFFFu) != 0u;
                          if (!grass && (value >> 18 & 1u) != 0u) {
                            for (int y = -1; y <= 1; y++) {
                              for (int x = -1; x <= 1; x++) {
                                uint beside = valueAt(square + ivec2(x, y));
                                grass = grass || ((beside & 0xFFFFu) != 0u && (beside >> 19 & 1u) == 0u);
                              }
                            }
                          }
                          if (!grass) return;
                          vec2 place = vec2(square) + 0.5;
                          float tallest = HEIGHTS[GRASS_WILD].y * uHeightScale;
                          vec3 foot = vec3(-place.x, 0.0, -place.y) - uSceneOrigin;
                          vec3 center = reliefLifted(foot) + vec3(0.0, 0.5 * tallest, 0.0);
                          float spread = 0.72 + tallest * BLADE_SPREAD;
                          if (uReliefGrid.z > 0.0) {
                            float lift = center.y - foot.y - 0.5 * tallest;
                            float variation = 0.0;
                            for (int y = -1; y <= 1; y += 2) {
                              for (int x = -1; x <= 1; x += 2) {
                                float corner = relief(reliefWorld(foot) + 0.5 * vec2(x, y)) * SQRT6;
                                variation = max(variation, abs(corner - lift));
                              }
                            }
                            spread += variation;
                          }
                          if (outsideView(center, spread)) return;
                          uint slot = atomicAdd(tierCount[GRASS_FAR], 1u);
                          blades[uint(uOPGrassTileQueue) + slot] = vec4(float(index), 0.0, 0.0, 0.0);
                        }

                        void main() {""")
                .replace(FINISH, """
                        if (i == 0u) {
                              if (uOPOverheadGrass == 1) tierCount[GRASS_FAR] = 0u;
                              finish();
                            }""")
                .replace(CANDIDATE, """
                        if (uOPOverheadGrass == 2) {
                            oldPointQueueGrassTile(i);
                            return;
                          }
                          bool nearestGrass = uOPOverheadGrass == 1;
                          uint tileCount = uint(uSquaresX * uSquaresY);
                          uint per = uint(uSlots);
                          if (nearestGrass && uOPGrassTileQueue > 0) {
                            uint queued = tierCount[GRASS_FAR];
                            if (queued == 0u) return;
                            per = min(uint(uBlades), uint(uNearMost) / queued);
                            if (i >= per * queued) return;
                          }
                          uint squareIndex = i / per, k = i % per;
                          if (nearestGrass && uOPGrassTileQueue > 0) {
                            squareIndex = uint(blades[uint(uOPGrassTileQueue) + squareIndex].x);
                          } else if (nearestGrass && uOPGrassTileStride > 1) {
                            uint first = squareIndex * uint(uOPGrassTileStride);
                            if (first >= tileCount) return;
                            ivec2 tile = ivec2(uCorner)
                                + ivec2(int(first) % uSquaresX, int(first) / uSquaresX);
                            uint span = min(uint(uOPGrassTileStride), tileCount - first);
                            squareIndex = first + min(uint(grassHash(tile, 0u).z * float(span)), span - 1u);
                          }
                          if (squareIndex >= tileCount) return;""")
                .replace(BAND_DENSITY, """
                        float away = nearestGrass ? 0.0 : length(place - uEyeWorld);
                          float share = nearestGrass ? 1.0 : shareAt(away);
                          float u = (float(k) + 0.5) / float(nearestGrass ? int(per) : uBlades);""")
                .replace(BAND_TEST, "if ((!nearestGrass && (away < uInner || away >= uOuter)) || u >= share) return;")
                .replace(CAMERA_DISTANCE,
                        "float fromEye = nearestGrass ? 0.0 : distance(foot, uEye);")
                .replace(TIER_TEST, "int tier = nearestGrass ? GRASS_NEAR : fromEye < uLodReach")
                .replace(RANGE_FADE, "(nearestGrass ? 1.0 : " + RANGE_FADE + ")")
                .replace(PIXEL_FADE, "if (!nearestGrass) { " + PIXEL_FADE + " }")
                .replace(CAMERA_DIRECTION,
                        "vec2 toEye = uOPOverheadGrass == 1"
                                + " ? -vec2(uViewProjection[0].w, uViewProjection[2].w) : uEye.xz - foot.xz;")
                .replace(WIDTH_SCALE, "(nearestGrass ? 1.0 : " + WIDTH_SCALE + ")");
    }

    public static void beginDraw(FrameContext frame) {
        if (frame != null && frame.scene != null && CameraDrawing.drawTopDown()) {
            current.set(new Draw(frame));
        }
    }

    public static void endDraw() {
        current.remove();
    }

    public static void afterStorageBegin(boolean cards, GlProgram program) {
        if (!program.has(ACTIVE)) {
            return;
        }
        program.setInt(ACTIVE, 0);
        Draw draw = current.get();
        if (draw != null && !cards) {
            draw.program = program;
        }
    }

    public static boolean dispatch(Object[] arguments) {
        Draw draw = current.get();
        // The other overload takes two floats and returns the native table of distance bands.
        if (draw == null || draw.program == null || arguments.length != 4
                || !(arguments[2] instanceof Double) || !(arguments[1] instanceof Integer blades) || blades <= 0) {
            return false;
        }
        GlProgram program = draw.program;
        int nearLocation = program.location("uNearMost");
        int farLocation = program.location("uFarMost");
        int firstLocation = program.location("uFarFirst");
        int id = program.id();
        if (id == 0 || nearLocation < 0 || farLocation < 0 || firstLocation < 0) {
            return false;
        }
        int near = glGetUniformi(id, nearLocation);
        int far = glGetUniformi(id, farLocation);
        int farFirst = glGetUniformi(id, firstLocation);
        int capacity = near + far;
        if (farFirst != near || near < 0 || far < 0 || capacity <= 0 || capacity % DRAW_GROUP_SIZE != 0) {
            return false;
        }

        Tiles tiles = visibleTiles(draw.frame);
        int squareCount = tiles.width * tiles.height;
        // One vec4 per possible tile, at the end of the existing blade buffer. Round to a whole draw group.
        int queueGroups = 1 + (squareCount - 1) / (DRAW_GROUP_SIZE * VEC4S_PER_BLADE);
        int temporaryBlades = queueGroups * DRAW_GROUP_SIZE;
        boolean queue = program.has(TILE_QUEUE) && capacity - temporaryBlades >= squareCount;
        int usable = queue ? capacity - temporaryBlades : capacity;
        int stride = queue ? 1 : 1 + (squareCount - 1) / usable;
        int sampledSquares = 1 + (squareCount - 1) / stride;
        int slots = queue ? blades : Math.min(blades, usable / sampledSquares);
        int candidates = (int) Math.min(usable, (long) sampledSquares * slots);

        // The two native ranges are consecutive. Give the usable capacity to the ordinary near draw.
        program.setInt("uNearMost", usable);
        program.setInt("uFarMost", 0);
        program.setInt("uFarFirst", capacity);
        program.setInt("uSquaresX", tiles.width);
        program.setInt("uSquaresY", tiles.height);
        program.setInt("uSlots", slots);
        program.set("uCorner", tiles.x, tiles.y);
        program.set("uViewProjection", draw.frame.viewProjection);
        program.setInt(STRIDE, stride);
        if (program.has(TILE_QUEUE)) {
            program.setInt(TILE_QUEUE, queue ? usable * VEC4S_PER_BLADE : 0);
        }
        if (queue) {
            program.setInt(ACTIVE, 2);
            glDispatchCompute(1 + (squareCount - 1) / COMPUTE_GROUP_SIZE, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }
        program.setInt(ACTIVE, 1);
        glDispatchCompute(1 + (candidates - 1) / COMPUTE_GROUP_SIZE, 1, 1);
        draw.nearest = true;

        if (!reportedActive) {
            reportedActive = true;
            System.out.println("[OldPoint] overhead grass: nearest profile across the visible ground, capacity="
                    + capacity);
        }
        return true;
    }

    static Tiles visibleTiles(FrameContext frame) {
        double originX = frame.scene.originX;
        double originZ = frame.scene.originZ;
        int mapX = (int) Math.floor(-originX) - MAP_SIDE / 2;
        int mapY = (int) Math.floor(-originZ) - MAP_SIDE / 2;
        Tiles map = new Tiles(mapX, mapY, MAP_SIDE, MAP_SIDE);
        // With relief enabled, allow any map tile into the GPU's terrain-aware frustum test.
        if (frame.scene.relief != null && frame.scene.relief.levels() > 0f) {
            return map;
        }
        double ground = -frame.scene.originY;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        Vector3f ray = new Vector3f();
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? -1f : 1f;
            float y = (corner & 2) == 0 ? -1f : 1f;
            frame.invViewProjection.transformProject(x, y, 0f, ray).sub(frame.eyeX, frame.eyeY, frame.eyeZ);
            if (!ray.isFinite() || ray.y >= -0.0001f) {
                return map;
            }
            // Include grass overhanging the screen edges, even if its root is outside the view.
            for (int height = 0; height <= BLADE_MARGIN; height += BLADE_MARGIN) {
                double distance = Math.max(0.0, (ground + height - frame.eyeY) / ray.y);
                double worldX = -(originX + frame.eyeX + distance * ray.x);
                double worldY = -(originZ + frame.eyeZ + distance * ray.z);
                minX = Math.min(minX, worldX);
                minY = Math.min(minY, worldY);
                maxX = Math.max(maxX, worldX);
                maxY = Math.max(maxY, worldY);
            }
        }
        int left = Math.max(mapX, (int) Math.floor(minX) - BLADE_MARGIN);
        int top = Math.max(mapY, (int) Math.floor(minY) - BLADE_MARGIN);
        int right = Math.min(mapX + MAP_SIDE, (int) Math.ceil(maxX) + BLADE_MARGIN);
        int bottom = Math.min(mapY + MAP_SIDE, (int) Math.ceil(maxY) + BLADE_MARGIN);
        if (left >= right || top >= bottom) {
            return map;
        }
        return new Tiles(left, top, right - left, bottom - top);
    }

    public static void drawLighting(GlProgram program) {
        if (program.has(NEAR_LIGHT)) {
            Draw draw = current.get();
            program.setInt(NEAR_LIGHT, draw != null && draw.nearest ? 1 : 0);
        }
    }

    record Tiles(int x, int y, int width, int height) {}

    private static final class Draw {
        private final FrameContext frame;
        private GlProgram program;
        private boolean nearest;

        private Draw(FrameContext frame) {
            this.frame = frame;
        }
    }

    @Patch(className = "viewpoint.render.GrassPass", methodName = "draw", strictMatch = false)
    public static final class FramePatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) FrameContext frame) {
            OverheadGrass.beginDraw(frame);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            OverheadGrass.endDraw();
        }
    }

    @Patch(className = "viewpoint.render.GrassStorage", methodName = "begin", strictMatch = false)
    public static final class StoragePatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(1) boolean cards, @Patch.Argument(2) GlProgram program) {
            OverheadGrass.afterStorageBegin(cards, program);
        }
    }

    @Patch(className = "viewpoint.render.GrassPass", methodName = "bands", strictMatch = false)
    public static final class BandsPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.AllArguments Object[] arguments) {
            return OverheadGrass.dispatch(arguments);
        }
    }

    @Patch(className = "viewpoint.render.GrassStorage", methodName = "draw", strictMatch = false)
    public static final class LightingPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) GlProgram program) {
            OverheadGrass.drawLighting(program);
        }
    }
}
