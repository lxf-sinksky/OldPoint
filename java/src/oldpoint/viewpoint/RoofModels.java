package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.opengl.GL20;
import viewpoint.core.Frame;
import viewpoint.packs.ModelPacks;
import viewpoint.packs.PackBind;
import viewpoint.platform.GlProgram;
import viewpoint.render.PackModel;
import viewpoint.render.SceneData;

/** Mask selected roof instances, without cutting wall vertices in the same space. */
public final class RoofModels {
    private record Chunk(int x, int y, int floor) {}

    // A weak-key cache value must never hold its own SceneData key.
    private record State(Map<PackModel, Map<Chunk, Long>> models) {}

    private record Drawing(SceneData scene, State state) {}

    private static final Map<SceneData, State> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Drawing> drawing = new ThreadLocal<>();
    private static Field runsField;
    private static boolean reported;
    private static boolean reportedActive;
    private static final Map<PackModel, Boolean> roofAssets = new WeakHashMap<>();

    private RoofModels() {}

    static void reset(SceneData scene) {
        snapshots.remove(scene);
    }

    static void clear() {
        snapshots.clear();
        roofAssets.clear();
    }

    static boolean roofSprite(zombie.iso.sprite.IsoSprite sprite) {
        if (sprite == null) {
            return false;
        }
        var type = sprite.getType();
        if (type == zombie.iso.SpriteDetails.IsoObjectType.WestRoofB
                || type == zombie.iso.SpriteDetails.IsoObjectType.WestRoofM
                || type == zombie.iso.SpriteDetails.IsoObjectType.WestRoofT) {
            return true;
        }
        if (roofName(sprite.name)) {
            return true;
        }
        var props = sprite.getProperties();
        if (props.has(zombie.iso.SpriteDetails.IsoFlagType.isEave)) {
            return true;
        }
        String name = props.get("CustomName");
        return name != null
                && (name.equalsIgnoreCase("Canopy")
                        || name.equalsIgnoreCase("Awning")
                        || name.equalsIgnoreCase("Roof"));
    }

    static boolean roofName(String name) {
        return RoofIdentity.name(name);
    }

    static boolean roofObject(zombie.iso.IsoObject object, zombie.iso.sprite.IsoSprite sprite, PackBind bind) {
        if (roofSprite(sprite)) {
            return true;
        }
        if (bind != null
                && bind.model.obj != null
                && roofAssets.computeIfAbsent(bind.model, model -> RoofIdentity.asset(model.obj.getPath()))) {
            return true;
        }
        if (sprite != null
                && sprite.name != null
                && sprite.name.startsWith("tilefix_markers_")
                && object.hasModData()) {
            Object original = object.getModData().rawget("tilefixFrom");
            if (original instanceof String from) {
                return roofName(from);
            }
        }
        return false;
    }

    static void capture(Frame frame, List<RoofCutaway.Model> selected) {
        Map<PackModel, Map<Chunk, Long>> models = new HashMap<>();
        for (var roof : selected) {
            Chunk chunk = new Chunk(Math.floorDiv(roof.x(), 8), Math.floorDiv(roof.y(), 8), roof.floor());
            long bit = 1L << (Math.floorMod(roof.y(), 8) * 8 + Math.floorMod(roof.x(), 8));
            models.computeIfAbsent(roof.model(), k -> new HashMap<>()).merge(chunk, bit, (a, b) -> a | b);
        }
        Map<PackModel, Map<Chunk, Long>> frozen = new HashMap<>();
        models.forEach((model, masks) -> frozen.put(model, Map.copyOf(masks)));
        snapshots.put(frame.scene, new State(Map.copyOf(frozen)));
    }

    public static void begin(SceneData scene, GlProgram program) {
        drawing.remove();
        // Default shadow shaders lack uOPInterior; Iris shadow draws set it to
        // zero. Retain roof shadow casters to avoid changing room illumination.
        if (!CameraDrawing.drawTopDown()
                || !program.has("uOPInterior")
                || GL20.glGetUniformi(program.id(), program.location("uOPInterior")) == 0) {
            return;
        }
        State state = snapshots.get(scene);
        if (state != null && !state.models.isEmpty()) {
            drawing.set(new Drawing(scene, state));
        }
    }

    public static void end() {
        drawing.remove();
    }

    public static void mask(Object value, int run) {
        Drawing context = drawing.get();
        if (context == null) {
            return;
        }
        State state = context.state;
        Map<Chunk, Long> masks = state.models.get((PackModel) value);
        if (masks == null) {
            return;
        }
        try {
            if (runsField == null) {
                runsField = Class.forName("viewpoint.render.PackDraws").getDeclaredField("runs");
                runsField.setAccessible(true);
            }
            int[] runs = (int[]) runsField.get(null);
            int at = run * 4;
            if (at < 0 || at + 3 >= runs.length) {
                return;
            }
            int mesh = runs[at];
            SceneData scene = context.scene;
            if (mesh < 0 || mesh >= scene.meshCount) {
                return;
            }
            int x = (int) Math.round(-(scene.meshOffsets[mesh * 3] + scene.originX));
            int y = (int) Math.round(-(scene.meshOffsets[mesh * 3 + 2] + scene.originZ));
            Long hidden = masks.get(new Chunk(Math.floorDiv(x, 8), Math.floorDiv(y, 8), scene.meshes[mesh].level));
            if (hidden == null) {
                return;
            }
            long visible = Integer.toUnsignedLong(runs[at + 2]) | ((long) runs[at + 3] << 32);
            if (!reportedActive && (visible & hidden) != 0) {
                reportedActive = true;
                PackModel model = (PackModel) value;
                System.out.println(
                        "[OldPoint] roof instance mask active: "
                                + (model.obj == null ? model.index : model.obj.getName()));
            }
            visible &= ~hidden;
            runs[at + 2] = (int) visible;
            runs[at + 3] = (int) (visible >>> 32);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] roof model cutaway unavailable: " + error);
            }
        }
    }
}
