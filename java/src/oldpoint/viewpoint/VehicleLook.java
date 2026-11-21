package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;

/** Let native mouse observation take priority over automatic driving pan. */
public final class VehicleLook {
    private static final ThreadLocal<Boolean> updating = new ThreadLocal<>();

    private VehicleLook() {}

    public static void begin() {
        updating.set(Boolean.TRUE);
    }

    public static void end() {
        updating.remove();
    }

    public static boolean drivingPan(boolean original) {
        IsoPlayer player = IsoPlayer.getInstance();
        return original && !(Boolean.TRUE.equals(updating.get()) && Bridge.topDownActive()
                && player != null && player.getVehicle() != null && player.isAimControlActive());
    }

    public static void observation(IsoPlayer player) {
        if (Bridge.topDownActive() && player != null && player.getVehicle() != null
                && player.isLookingWhileInVehicle()) {
            player.setIsAiming(false);
        }
    }

    public static boolean cameraAiming(IsoPlayer player, boolean original) {
        return original || Boolean.TRUE.equals(updating.get()) && Bridge.topDownActive()
                && player != null && player.getVehicle() != null && player.isLookingWhileInVehicle();
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "updateWhileInVehicle", strictMatch = true)
    public static final class ObservationPatch {
        @Patch.OnExit
        public static void exit(@Patch.This IsoPlayer player) {
            VehicleLook.observation(player);
        }
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "isAiming", strictMatch = true)
    public static final class CameraAimPatch {
        @Patch.OnExit
        public static void exit(@Patch.This IsoPlayer player, @Patch.Return(readOnly = false) boolean result) {
            result = VehicleLook.cameraAiming(player, result);
        }
    }

    @Patch(className = "zombie.iso.PlayerCamera", methodName = "update", strictMatch = true)
    public static final class CameraPatch {
        @Patch.OnEnter
        public static void enter() {
            VehicleLook.begin();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            VehicleLook.end();
        }
    }

    @Patch(className = "zombie.core.Core", methodName = "getOptionPanCameraWhileDriving", strictMatch = true)
    public static final class OptionPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            result = VehicleLook.drivingPan(result);
        }
    }
}
