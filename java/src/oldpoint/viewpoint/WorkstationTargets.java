package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.entity.ComponentType;
import zombie.iso.IsoObject;

/**
 * Build 42 workstations (coffee machine, toaster, forge...) are game entities whose actions come from their
 * crafting UI, not from loot or Viewpoint's action list. Viewpoint finds no action for them and lets its mouse ray
 * pass through to the counter underneath, so they could not be hovered or right-clicked. Keep them aimable.
 */
public final class WorkstationTargets {
    private static boolean failed;

    private WorkstationTargets() {}

    public static boolean passes(IsoObject object, boolean original) {
        if (!original || failed || object == null) {
            return original;
        }
        try {
            boolean workstation = object.hasComponentAny(
                    ComponentType.UiConfig, ComponentType.CraftBench, ComponentType.CraftLogic);
            return !workstation;
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            System.out.println("[OldPoint] workstation targeting disabled: " + error);
            return original;
        }
    }

    @Patch(className = "viewpoint.interact.InteractActions", methodName = "passes", strictMatch = false)
    public static final class PassesPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) IsoObject object, @Patch.Return(readOnly = false) boolean result) {
            result = WorkstationTargets.passes(object, result);
        }
    }
}
