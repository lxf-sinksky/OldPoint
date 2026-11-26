package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.light.SourceRegister", methodName="lamp", strictMatch=false)
public final class Rc2AuthenticGlowsticksLampPatch {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) long key) {
        return key >= Integer.MIN_VALUE && key <= Integer.MAX_VALUE && Rc2AuthenticGlowsticks.replaces((int) key);
    }
}
