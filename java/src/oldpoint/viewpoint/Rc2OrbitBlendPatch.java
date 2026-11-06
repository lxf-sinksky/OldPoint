package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.SceneDrawer", methodName="between", strictMatch=true)
public final class Rc2OrbitBlendPatch {
    @Patch.OnExit
    public static void exit(@Patch.This Object object, @Patch.Argument(value=0) long l, @Patch.Return float f) {
        Rc2OrbitInterpolation.begin(object, f);
    }
}
