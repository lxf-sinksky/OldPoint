package oldpoint.viewpoint;

import fmod.fmod.SoundListener;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.game.Hearing", methodName="listen", strictMatch=true)
public final class Rc2OverheadHearingPatch {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(value=0) SoundListener soundListener) {
        if (!Bridge.topDownActive()) {
            return false;
        }
        Rc2OverheadHearing.listen(soundListener);
        return true;
    }
}
