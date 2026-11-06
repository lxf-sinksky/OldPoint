package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.light.SourceRegister", methodName="lamp", strictMatch=false)
public final class Rc2AuthenticGlowsticksLampPatch {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(value=0) int n) {
        return Rc2AuthenticGlowsticks.replaces(n);
    }
}
