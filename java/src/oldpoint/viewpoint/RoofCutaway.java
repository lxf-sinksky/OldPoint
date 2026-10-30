package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import viewpoint.packs.ModelPacks;
import viewpoint.render.PackModel;
import viewpoint.world.WorldMesher;
import zombie.characters.IsoGameCharacter;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/** Building-wide roof selection. Cache identities; apply the current collapsed-building set to every roof piece. */
final class RoofCutaway {
    private static final float STOREY = 2.4494896f;
    private static final int BASE_RADIUS = 16, MAX_RADIUS = 48, MAX_CANDIDATES = 4096;
    private static final long CACHE_NANOS = 250_000_000L;
    private static volatile boolean debug;
    private static Object owner, cell;
    private static int x, y, floor, top;
    private static CaptureScan scan;
    private static ArrayList<Candidate> pending;
    private static List<Candidate> complete = List.of();
    private static long completedAt;
    private static volatile boolean scanning;

    record Model(PackModel model, int x, int y, int floor) {}
    private record Candidate(RoofSelection.Box box, RoofBounds art, Model model, BuildingDef building) {}
    record Result(List<RoofBounds> rules, List<Model> models) {}

    private RoofCutaway() {}

    static void debug(boolean value) {
        debug = value;
    }

    static void clear() {
        owner = null;
        cell = null;
        scan = null;
        pending = null;
        complete = List.of();
        completedAt = 0;
        scanning = false;
    }

    static Result capture(IsoGameCharacter player, List<Layout.Rect> regions,
                          Set<BuildingDef> selectedBuildings, int roofTop) {
        if (player.getCurrentSquare() == null || IsoWorld.instance.getCell() == null) {
            clear();
            return new Result(List.of(), List.of());
        }
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        int level = (int) Math.floor(player.getZ());
        var world = IsoWorld.instance.getCell();
        boolean same = owner == player && cell == world && floor == level && top == roofTop
                && Math.abs(cx - x) <= 4 && Math.abs(cy - y) <= 4;
        if (!same) {
            clear();
            owner = player;
            cell = world;
            x = cx;
            y = cy;
            floor = level;
            top = roofTop;
        }
        long now = System.nanoTime();
        if (scan == null && (completedAt == 0 || now - completedAt >= CACHE_NANOS)) {
            int left = cx - BASE_RADIUS, right = cx + BASE_RADIUS;
            int minY = cy - BASE_RADIUS, maxY = cy + BASE_RADIUS;
            for (var r : regions) {
                left = Math.min(left, r.x() - 2);
                right = Math.max(right, r.x() + r.width() + 1);
                minY = Math.min(minY, r.y() - 2);
                maxY = Math.max(maxY, r.y() + r.height() + 1);
            }
            scan = new CaptureScan(Math.max(cx - MAX_RADIUS, left), Math.min(cx + MAX_RADIUS, right),
                    Math.max(cy - MAX_RADIUS, minY), Math.min(cy + MAX_RADIUS, maxY), level - 1, Math.min(31, roofTop));
            pending = new ArrayList<>();
        }
        if (scan != null) {
            scan.begin();
            while (scan.next()) {
                var square = world.getGridSquare(scan.x, scan.y, scan.z);
                if (square == null) {
                    continue;
                }
                var objects = square.getObjects();
                for (int i = scan.objectIndex; i < objects.size(); i++) {
                    if (!scan.allowObject(i)) {
                        break;
                    }
                    var object = objects.get(i);
                    var sprite = object.getSprite();
                    if (sprite == null) {
                        continue;
                    }
                    var bind = ModelPacks.bind(sprite);
                    if (!RoofModels.roofObject(object, sprite, bind)) {
                        continue;
                    }
                    int sx = scan.x, sy = scan.y, sz = scan.z;
                    float base = sz * STOREY + WorldMesher.rise(object);
                    RoofSelection.Box box = null;
                    Model model = null;
                    if (bind != null && bind.model.state() == PackModel.RESIDENT) {
                        model = new Model(bind.model, sx, sy, sz);
                        float[] bounds = bind.model.bounds();
                        if (bounds != null && bounds.length >= 6) {
                            float ax = Float.POSITIVE_INFINITY, ay = ax, az = ax;
                            float bx = Float.NEGATIVE_INFINITY, by = bx, bz = bx;
                            float[] point = new float[3];
                            for (int corner = 0; corner < 8; corner++) {
                                bind.place(bounds[(corner & 1) == 0 ? 0 : 3],
                                        bounds[(corner & 2) == 0 ? 1 : 4],
                                        bounds[(corner & 4) == 0 ? 2 : 5],
                                        viewpoint.packs.PackBind.turns(sprite), point);
                                float px = sx + 0.5f + bind.x - point[0];
                                float py = sy + 0.5f + bind.y - point[2];
                                float pz = base + bind.z + point[1];
                                ax = Math.min(ax, px);
                                ay = Math.min(ay, py);
                                az = Math.min(az, pz);
                                bx = Math.max(bx, px);
                                by = Math.max(by, py);
                                bz = Math.max(bz, pz);
                            }
                            box = new RoofSelection.Box(ax, ay, az, bx, by, bz);
                        }
                    }
                    RoofBounds art = null;
                    var texture = sprite.getTextureForCurrentFrame(object.getDir(), object);
                    if (texture != null && texture.isReady()) {
                        float[] b = ReadyMeshes.bounds(sprite, texture, null);
                        boolean generated = sprite.name != null && viewpoint.world.MapRoofs.vanilla(sprite);
                        if (b != null || generated) {
                            float ax = b == null ? -0.3f : -b[3];
                            float ay = b == null ? level * STOREY + 1.9f - base : b[1];
                            float az = b == null ? -0.3f : -b[5];
                            float bx = b == null ? 1.3f : -b[0];
                            float by = b == null ? (level + 1) * STOREY + 0.8f - base : b[4];
                            float bz = b == null ? 1.3f : -b[2];
                            if (generated) {
                                ax = Math.min(ax, -0.3f);
                                az = Math.min(az, -0.3f);
                                bx = Math.max(bx, 1.3f);
                                bz = Math.max(bz, 1.3f);
                                ay = Math.min(ay, level * STOREY + 1.9f - base);
                                by = Math.max(by, (level + 1) * STOREY + 0.8f - base);
                            }
                            float[] rect = WorldMesher.textureMapping(texture);
                            int atlas = texture.getTextureId().getID();
                            art = new RoofBounds(sx + ax - 0.025f, sy + az - 0.025f,
                                    base + ay - 0.025f, sx + bx + 0.025f, sy + bz + 0.025f,
                                    base + by + 0.025f, rect[0], rect[1], rect[2], rect[3], atlas);
                            if (box == null) {
                                box = new RoofSelection.Box(art.x(), art.y(), art.up(),
                                        art.x2(), art.y2(), art.up2());
                            }
                        }
                    }
                    if (box == null && sz > level) {
                        box = new RoofSelection.Box(sx, sy, base, sx + 1, sy + 1, base + STOREY);
                    }
                    // Bound the scan's retained data even with pathological custom maps.
                    if (pending.size() < MAX_CANDIDATES) {
                        pending.add(new Candidate(box, art, model, building(square, world)));
                    }
                }
            }
            scan.finish();
            if (scan.done()) {
                complete = List.copyOf(pending);
                completedAt = now;
                scan = null;
                pending = null;
            }
        }
        scanning = scan != null;
        ArrayList<RoofBounds> rules = new ArrayList<>();
        ArrayList<Model> models = new ArrayList<>();
        // Publish only complete identity scans. The current building set controls all pieces together.
        for (var candidate : complete) {
            Boolean selectedOwner = candidate.building == null ? null : selectedBuildings.contains(candidate.building);
            int reason = RoofSelection.reason(candidate.box, level, selectedOwner, regions);
            boolean hide = reason == RoofSelection.COVER;
            if (candidate.art != null) {
                var a = candidate.art;
                rules.add(a.withHidden(hide));
            }
            if (hide && candidate.model != null) {
                models.add(candidate.model);
            }
            if (candidate.model != null && candidate.box != null && candidate.box.valid()) {
                var b = candidate.box;
                // Model rules select the exact packed asset, independently of its texture.
                rules.add(RoofBounds.model(b, candidate.model.model.index, hide));
            }
        }
        return new Result(List.copyOf(rules), List.copyOf(models));
    }

    private static BuildingDef building(IsoGridSquare square, IsoCell world) {
        if (square.associatedBuilding != null) {
            return square.associatedBuilding;
        }
        if (square.getBuilding() != null) {
            return square.getBuilding().getDef();
        }
        if (square.getRoofHideBuilding() != null) {
            return square.getRoofHideBuilding().getDef();
        }
        for (int z = square.getZ() - 1; z >= -32; z--) {
            var below = world.getGridSquare(square.getX(), square.getY(), z);
            if (below == null) {
                continue;
            }
            if (below.associatedBuilding != null) {
                return below.associatedBuilding;
            }
            if (below.getBuilding() != null) {
                return below.getBuilding().getDef();
            }
            if (below.getRoofHideBuilding() != null) {
                return below.getRoofHideBuilding().getDef();
            }
        }
        return null;
    }

    static boolean preview() {
        return debug;
    }

    static void draw(String title, String waiting) {
        if (!preview() || !CameraController.topDownActive() || owner == null) {
            return;
        }
        TextManager.instance.DrawString(UIFont.Small, 20, 180,
                title + (scanning ? " / " + waiting : ""), 1, 1, 1, 1);
    }
}
