package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import se.krka.kahlua.vm.KahluaTable;
import viewpoint.packs.ModelPacks;
import viewpoint.render.MousePick;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.input.Mouse;
import zombie.iso.IsoWorld;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/** Debug the actual rendered depth hit. Nearby world objects are clues, never claimed as an exact source match. */
final class RayProbe {
    private static final double STOREY = 2.4494896;
    private static boolean enabled;
    private static MousePick.Hit previous;
    private static long received, nextDetails;
    private static List<String> details = List.of();

    private record Labels(String title, String empty, String point, String ray, String rules,
                          String nearby, String roof, String other, String note, String normal, String noPlane) {
        static Labels from(KahluaTable values) {
            return new Labels(text(values, "title"), text(values, "empty"), text(values, "point"),
                    text(values, "ray"), text(values, "rules"), text(values, "nearby"), text(values, "roof"),
                    text(values, "other"), text(values, "note"), text(values, "normal"), text(values, "noPlane"));
        }

        private static String text(KahluaTable values, String name) {
            Object value = values.rawget(name);
            return value instanceof String text ? text : "";
        }
    }

    private RayProbe() {}

    static void configure(boolean value) {
        if (enabled != value) {
            previous = null;
            details = List.of();
            received = 0;
            nextDetails = 0;
        }
        enabled = value;
        RayPlane.configure(value);
    }

    static void draw(KahluaTable text) {
        if (!enabled || !CameraController.topDownActive() || Pointer.overUI()
                || IsoWorld.instance == null || IsoWorld.instance.getCell() == null) {
            return;
        }
        Labels labels = Labels.from(text);
        int mx = Mouse.getXA(), my = Mouse.getYA();
        int width = Core.getInstance().getScreenWidth(), height = Core.getInstance().getScreenHeight();
        MousePick.ask = new MousePick.Ask(mx, my, width, height);
        var plane = RayPlane.current();
        var hit = plane == null ? MousePick.hit : plane.hit();
        long now = System.nanoTime();
        if (hit != previous) {
            if (previous == null || hit == null || Math.abs(hit.ask().x() - previous.ask().x()) > 3
                    || Math.abs(hit.ask().y() - previous.ask().y()) > 3) {
                nextDetails = 0;
            }
            previous = hit;
            received = now;
        }
        int left = 20, row = 230;
        line(left, row, labels.title());
        if (hit == null || !fresh(mx, my, width, height,
                hit.ask().x(), hit.ask().y(), hit.ask().screenWidth(), hit.ask().screenHeight(), now - received)
                || !Double.isFinite(hit.x()) || !Double.isFinite(hit.y()) || !Double.isFinite(hit.z())) {
            line(left, row + 20, labels.empty());
            return;
        }
        float[] ray = new float[6];
        if (!Pointer.interactionRay(ray)) {
            line(left, row + 20, labels.empty());
            return;
        }
        line(left, row += 20, String.format(Locale.ROOT, "%s: %.3f, %.3f, %.3f",
                labels.point(), hit.x(), hit.y(), hit.z()));
        line(left, row += 20, String.format(Locale.ROOT, "%s: %.2f, %.2f, %.2f / %.3f, %.3f, %.3f",
                labels.ray(), ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]));
        if (plane != null) {
            var n = plane.normal();
            line(left, row += 20, String.format(Locale.ROOT, "%s: %.3f, %.3f, %.3f",
                    labels.normal(), -n.x(), -n.z(), n.y()));
            RayPlane.draw(plane);
        } else {
            line(left, row += 20, labels.noPlane());
        }
        int tiles = 0, models = 0, hidden = 0;
        var state = Interior.latestSnapshot();
        if (state != null) {
            for (var roof : state.roofs()) {
                if (inside(hit.x(), hit.y(), hit.z() * STOREY,
                        roof.x(), roof.y(), roof.up(), roof.x2(), roof.y2(), roof.up2())) {
                    if (roof.model() >= 0) {
                        models++;
                    } else {
                        tiles++;
                    }
                    if (roof.hidden()) {
                        hidden++;
                    }
                }
            }
        }
        line(left, row += 20, String.format(Locale.ROOT, "%s: %d / %d / %d", labels.rules(), tiles, models, hidden));
        line(left, row += 20, labels.nearby());
        if (now >= nextDetails) {
            details = nearby(hit, labels.roof(), labels.other());
            nextDetails = now + 150_000_000L;
        }
        for (var detail : details) {
            line(left, row += 20, detail);
        }
        line(left, row + 20, labels.note());
        // This marks the depth sample on screen; the camera ray projects to this same pixel.
        SpriteRenderer.instance.renderlinef(null, mx - 9, my, mx + 9, my, 0, 1, 1, 1, 2);
        SpriteRenderer.instance.renderlinef(null, mx, my - 9, mx, my + 9, 0, 1, 1, 1, 2);
    }

    private static List<String> nearby(MousePick.Hit hit, String roofLabel, String otherLabel) {
        ArrayList<String> roofRows = new ArrayList<>(), otherRows = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        int x = (int) Math.floor(hit.x()), y = (int) Math.floor(hit.y()), z = (int) Math.floor(hit.z());
        var cell = IsoWorld.instance.getCell();
        for (int dz = 0; dz >= -3; dz--) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    var square = cell.getGridSquare(x + dx, y + dy, z + dz);
                    if (square == null) {
                        continue;
                    }
                    var objects = square.getObjects();
                    for (int i = 0; i < objects.size(); i++) {
                        var object = objects.get(i);
                        var sprite = object.getSprite();
                        if (sprite == null) {
                            continue;
                        }
                        var pack = ModelPacks.bind(sprite);
                        boolean roof = RoofModels.roofObject(object, sprite, pack);
                        String name = sprite.name == null ? "?" : sprite.name;
                        String model = pack == null || pack.model.obj == null ? "" : " / " + pack.model.obj.getName();
                        String value = (roof ? roofLabel : otherLabel) + " " + name + model
                                + " [" + square.getX() + "," + square.getY() + "," + square.getZ() + "]";
                        if (seen.add(value)) {
                            var rows = roof ? roofRows : otherRows;
                            if (rows.size() < 6) {
                                rows.add(value);
                            }
                        }
                    }
                }
            }
        }
        roofRows.addAll(otherRows.subList(0, Math.min(otherRows.size(), 6 - roofRows.size())));
        return List.copyOf(roofRows);
    }

    private static boolean fresh(int x, int y, int width, int height, int hitX, int hitY,
                         int hitWidth, int hitHeight, long age) {
        return width > 0 && height > 0 && x >= 0 && x < width && y >= 0 && y < height
                && width == hitWidth && height == hitHeight
                && Math.abs(x - hitX) <= 3 && Math.abs(y - hitY) <= 3
                && age >= 0 && age < 500_000_000L;
    }

    private static boolean inside(double x, double y, double up,
                          float minX, float minY, float minUp, float maxX, float maxY, float maxUp) {
        double margin = 0.05;
        return x >= minX - margin && x <= maxX + margin && y >= minY - margin && y <= maxY + margin
                && up >= minUp - margin && up <= maxUp + margin;
    }

    private static void line(int x, int y, String value) {
        TextManager.instance.DrawString(UIFont.Small, x + 1, y + 1, value, 0, 0, 0, 1);
        TextManager.instance.DrawString(UIFont.Small, x, y, value, 0.75, 1, 1, 1);
    }
}
