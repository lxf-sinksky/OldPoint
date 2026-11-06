package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.light.LightSources", methodName="update", strictMatch=false)
public final class Rc2AuthenticGlowsticksRefreshPatch {
    @Patch.OnEnter
    public static void enter() {
        Rc2AuthenticGlowsticks.refresh();
    }
}
