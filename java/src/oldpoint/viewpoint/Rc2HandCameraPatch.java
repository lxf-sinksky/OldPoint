package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.SceneDrawer", methodName="hand", strictMatch=true)
public final class Rc2HandCameraPatch {
    @Patch.OnEnter
    public static void enter() {
        Rc2HandCamera.enter();
    }

    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit() {
        Rc2HandCamera.exit();
    }
}
