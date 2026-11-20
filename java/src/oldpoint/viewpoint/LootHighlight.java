package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import viewpoint.render.SceneData;
import viewpoint.visibility.Edges;
import viewpoint.visibility.Owner;
import viewpoint.world.ChunkCache;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDeadBody;
import zombie.iso.objects.IsoWorldInventoryObject;

/**
 * Vanilla tints the container selected in the loot window (mouse wheel or button), but Viewpoint only outlines
 * what the cursor is on. While nothing is under the cursor, outline the selected loot container instead.
 */
public final class LootHighlight {
    private static volatile IsoObject selected;
    private static boolean failed;

    private LootHighlight() {}

    /** Called from Lua with the loot window's container object, or null when none should show. */
    public static void select(Object object) {
        selected = object instanceof IsoObject isoObject && !(object instanceof IsoPlayer) ? isoObject : null;
    }

    public static void apply(Frame frame) {
        IsoObject object = selected;
        if (failed || object == null || frame == null || !CameraController.topDownActive()) {
            return;
        }
        SceneData scene = frame.scene;
        boolean hovering = frame.lootObject != null
                || frame.lootSquare != null
                || frame.lootVehicle != null
                || frame.lootCharacter != null
                || scene.targetSquare >= 0;
        if (hovering) {
            return;
        }
        try {
            IsoGridSquare square = object.getSquare();
            if (square == null) {
                return;
            }
            if (object instanceof IsoWorldInventoryObject item) {
                // Loose items (a bag on the floor) are outlined through their own model.
                if (item.getItem() == null) {
                    return;
                }
                frame.lootSquare = square;
                frame.lootItems.clear();
                frame.lootItems.add(item.getItem());
                return;
            }
            boolean body = object instanceof IsoDeadBody;
            int index = square.getObjects().indexOf(object);
            if (index < 0 && !body) {
                return;
            }
            int local = Math.floorMod(square.getY(), 8) * 8 + Math.floorMod(square.getX(), 8);
            int kind = body || object.getSprite() == null ? 0 : Edges.ownerKind(object, object.getSprite());
            int owner = Owner.of(local, kind == Owner.NONE ? Owner.CONTENTS : kind);
            frame.lootSquare = square;
            frame.lootObject = object;
            scene.targetMesh = ChunkCache.meshOn(square);
            scene.targetOwner = owner;
            scene.targetSquare = Owner.square(owner);
            scene.targetObject = body ? Owner.BODIES : Owner.object(local, index);
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            System.out.println("[OldPoint] loot container highlight disabled: " + error);
        }
    }

    @Patch(className = "viewpoint.interact.LootMenu", methodName = "snapshot", strictMatch = false)
    public static final class SnapshotPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object frame) {
            LootHighlight.apply((Frame) frame);
        }
    }
}
