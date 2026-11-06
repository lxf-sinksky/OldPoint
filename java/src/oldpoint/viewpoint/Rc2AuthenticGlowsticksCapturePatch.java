package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;

@Patch(className="viewpoint.light.Torches", methodName="snapshot", strictMatch=false)
public final class Rc2AuthenticGlowsticksCapturePatch {
    @Patch.OnExit
    public static void exit(@Patch.Argument(value=0) Frame frame) {
        Rc2AuthenticGlowsticks.snapshot(frame);
    }
}
