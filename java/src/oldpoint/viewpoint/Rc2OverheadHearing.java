package oldpoint.viewpoint;

import fmod.fmod.SoundListener;
import fmod.javafmod;
import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.platform.Motion;
import zombie.characters.IsoPlayer;
import zombie.network.GameServer;
import zombie.scripting.objects.CharacterTrait;

/** Places the audio listener at the overhead camera in nightly Viewpoint. */
public final class Rc2OverheadHearing {
    private static final float LEVEL_UNITS = 3.0f;
    private static final float[] forward = new float[3];
    private static final float[] up = new float[3];
    private static final float[] velocity = new float[3];
    private static final Motion motion = new Motion();

    private Rc2OverheadHearing() {}

    public static void listen(SoundListener listener) {
        if (listener.index != 0 || !View.enabled || GameServer.server) {
            return;
        }
        FreeCam.Place place = FreeCam.centre();
        IsoPlayer player = IsoPlayer.players[0];
        boolean freeCamera = place != null && player != null && !player.hasTrait(CharacterTrait.DEAF);
        float x = freeCamera ? (float) place.x() : listener.x;
        float y = freeCamera ? (float) place.y() : listener.y;
        float z = (freeCamera ? (float) place.z() : listener.z) * LEVEL_UNITS;
        if (!freeCamera && player != null) {
            player.hasTrait(CharacterTrait.DEAF);
        }
        long now = System.nanoTime();
        motion.place(x, y, z, now);
        motion.velocity(now, velocity);
        orient(place != null ? place.yaw() : Look.yaw,
                place != null ? place.pitch() : Look.pitch, forward, up);
        javafmod.FMOD_Studio_Listener3D(0, x, y, z,
                velocity[0], velocity[1], velocity[2],
                forward[0], forward[1], forward[2], up[0], up[1], up[2]);
    }

    public static float cameraZoom(float zoom) {
        return View.enabled ? 0.0f : zoom;
    }

    static void orient(float yaw, float pitch, float[] forward, float[] up) {
        float pitchCos = (float) Math.cos(pitch);
        float x = (float) Math.cos(yaw) * pitchCos;
        float y = (float) Math.sin(yaw) * pitchCos;
        float z = (float) Math.sin(pitch) * LEVEL_UNITS / 2.4494896f;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        forward[0] = x / length;
        forward[1] = y / length;
        forward[2] = z / length;
        float upX = -forward[2] * forward[0];
        float upY = -forward[2] * forward[1];
        float upZ = 1.0f - forward[2] * forward[2];
        float upLength = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        up[0] = upX / upLength;
        up[1] = upY / upLength;
        up[2] = upZ / upLength;
    }
}
