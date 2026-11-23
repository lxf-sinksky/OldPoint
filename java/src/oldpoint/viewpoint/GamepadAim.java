package oldpoint.viewpoint;

import zombie.characters.CharacterInputMode;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.Vector2;

/** Resolve the mapped aiming stick in screen space against the active overhead projection. */
final class GamepadAim {
    static volatile float gain = 0.5f;
    static volatile float lockGain = 0.5f;
    static volatile boolean assist, assistHead;
    static volatile boolean allowLock;
    private static final GamepadAim local = new GamepadAim();
    private IsoZombie manual;
    private IsoPlayer owner;
    private boolean waitForRelease;
    private boolean r3Held, wasDown;
    private final org.lwjgl.util.vector.Vector3f head = new org.lwjgl.util.vector.Vector3f();
    private long sampled, fullSince;
    private float trackedX, trackedY, trimX, trimY, panX, panY;

    static synchronized void resetIfIdle(IsoPlayer player) {
        if (!allowLock || local.owner != player || !CameraController.topDownActive() || !bound(player)
                || (!player.isAiming() && !player.isPrecisionAimKeyDown()) || player.isDead()) {
            local.manual = null;
            local.waitForRelease = false;
            local.r3Held = false;
            local.owner = player;
        }
    }

    static synchronized IsoZombie targetFor(IsoPlayer player) {
        if (local.manual == null || player == null || player != local.owner || !allowLock
                || !bound(player) || !CameraController.topDownActive()
                || !(player.isAiming() || player.isPrecisionAimKeyDown()) || !local.eligible(player, local.manual)) {
            return null;
        }
        return local.manual;
    }

    static synchronized float cameraAxis(boolean horizontal, float fallback) {
        if (local.manual == null) {
            return fallback;
        }
        return horizontal ? local.panX : local.panY;
    }
    private final float[] point = new float[2];
    private IsoZombie locked;
    private long nextSearch;

    static boolean bound(IsoGameCharacter actor) {
        return actor instanceof IsoPlayer p && p.isLocalPlayer() && p.getInputMode() == CharacterInputMode.GAMEPAD;
    }

    static synchronized boolean cursor(IsoPlayer p, int width, int height, Vector2 out) {
        return local.resolve(p, width, height, out);
    }

    private boolean resolve(IsoPlayer p, int width, int height, Vector2 out) {
        // Viewpoint suppresses the vanilla aiming-axis query while PadLook owns the stick.
        // Read the assigned controller axes directly, as PadLook does for its camera.
        int id = viewpoint.input.PadLook.bind(p);
        var input = zombie.GameWindow.GameInput;
        if (id < 0 || input.getAxisCount(id) < 4) {
            return false;
        }
        out.set(input.getAxisValue(id, 2), input.getAxisValue(id, 3));
        float x = out.x, y = out.y;
        float length = out.getLength();
        resetIfIdle(p);
        if (length < 0.90f) {
            waitForRelease = false;
        }
        long time = System.nanoTime();
        var pad = zombie.input.JoypadManager.instance.getFromControllerID(id);
        boolean r3 = pad != null && pad.isR3Pressed();
        if (allowLock && r3 && !r3Held && (p.isAiming() || p.isPrecisionAimKeyDown())) {
            acquire(p, time, x, y, width, height);
        }
        r3Held = r3;
        if (manual != null && track(p, x, y, length, time, out)) {
            return true;
        }
        if (!Float.isFinite(length) || length <= 0.15f || !Pointer.project(p.getX(), p.getY(),
                Pointer.groundHeight(p.getZ()), point, 0)) {
            locked = null;
            nextSearch = 0;
            return false;
        }
        float cx = point[0], cy = point[1];

        x /= length;
        y /= length;
        // Travel to the viewport edge along this bearing, including when the camera is panned.
        float margin = Math.min(32f, Math.min(width, height) * 0.05f);
        float edgeX = x > 0 ? width - margin - cx : cx - margin;
        float edgeY = y > 0 ? height - margin - cy : cy - margin;
        float limitX = Math.abs(x) < 0.0001f ? Float.POSITIVE_INFINITY : edgeX / Math.abs(x);
        float limitY = Math.abs(y) < 0.0001f ? Float.POSITIVE_INFINITY : edgeY / Math.abs(y);
        float travel = Math.min(1, (length - 0.15f) / 0.85f);
        float span = Math.max(1, Math.min(limitX, limitY)) * (float) Math.pow(travel, 1 / gain);
        out.set(cx + x * span, cy + y * span);
        if (waitForRelease) {
            return true;
        }
        if (!assist || p.getCell() == null || !(p.isAiming() || p.isPrecisionAimKeyDown())) {
            locked = null;
            nextSearch = 0;
            return true;
        }
        long now = System.nanoTime();
        if (now >= nextSearch) {
            nextSearch = now + 50_000_000L;
            IsoZombie best = null;
            float bestScore = Float.POSITIVE_INFINITY;
            for (var z : p.getCell().getZombieList()) {
                float score = score(p, z, cx, cy, x, y, span);
                if (score < bestScore) {
                    best = z;
                    bestScore = score;
                }
            }
            locked = best;
        }
        if (locked != null && Float.isFinite(score(p, locked, cx, cy, x, y, span))) {
            out.set(point[0], point[1]);
        }
        return true;
    }

    private boolean eligible(IsoPlayer p, IsoZombie z) {
        return !z.isDead() && z.getCurrentSquare() != null && z.getCell() == p.getCell()
                && (int) z.getZ() == (int) p.getZ() && z.getCurrentSquare().isCanSee(p.getIndex());
    }

    private final float[] feet = new float[2];

    private void acquire(IsoPlayer p, long now, float x, float y, int width, int height) {
        if (p.getCell() == null) {
            return;
        }
        IsoZombie nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (var z : p.getCell().getZombieList()) {
            double distance = p.DistToSquared(z);
            if (distance < best && eligible(p, z)
                    && Pointer.project(z.getX(), z.getY(), Pointer.groundHeight(z.getZ()), point, 0)
                    && point[0] >= 0 && point[0] < width && point[1] >= 0 && point[1] < height) {
                best = distance;
                nearest = z;
            }
        }
        if (nearest != null) {
            manual = nearest;
            wasDown = false;
            trackedX = manual.getX();
            trackedY = manual.getY();
            trimX = trimY = 0;
            panX = x;
            panY = y;
            sampled = now;
            fullSince = 0;
            waitForRelease = false;
        }
    }

    private boolean track(IsoPlayer p, float x, float y, float length, long now, Vector2 out) {
        if (length < 0.95f) {
            fullSince = 0;
        } else if (fullSince == 0) {
            fullSince = now;
        }
        if (!eligible(p, manual) || (fullSince != 0 && now - fullSince > 1_000_000_000L)) {
            waitForRelease = fullSince != 0 && now - fullSince > 1_000_000_000L;
            manual = null;
            return false;
        }
        double dt = Math.min(0.1, (now - sampled) / 1e9);
        sampled = now;
        double follow = -Math.expm1(-dt / 0.20);
        trackedX += (manual.getX() - trackedX) * follow;
        trackedY += (manual.getY() - trackedY) * follow;
        if (length > 0.15f) {
            trimX = (float) Math.max(-1.5, Math.min(1.5, trimX + 2.5 * dt * x * gain * lockGain));
            trimY = (float) Math.max(-1.5, Math.min(1.5, trimY + 2.5 * dt * y * gain * lockGain));
        }
        boolean down = manual.isProne() || manual.isOnFloor();
        if (down && !wasDown) {
            trimX = trimY = 0;
        }
        wasDown = down;
        double floor = Pointer.groundHeight(manual.getZ());
        if (!Pointer.project(trackedX, trackedY, floor, feet, 0)
                || !projectTarget(manual, trackedX, trackedY, down || (assist && assistHead))) {
            manual = null;
            return false;
        }
        float scale = Math.max(14, Math.abs(feet[1] - point[1]));
        out.set(point[0] + trimX * scale, point[1] + trimY * scale);
        return true;
    }

    private boolean projectTarget(IsoZombie z, float x, float y, boolean useHead) {
        if (useHead) {
            var anim = z.getAnimationPlayer();
            if (anim != null && anim.isReady() && !anim.isBoneTransformsNeedFirstFrame()) {
                anim.getBoneWorldPosition(zombie.core.skinnedmodel.model.SkeletonBone.Bip01_Head, head);
                return Pointer.project(x + (head.x - z.getX()), y + (head.y - z.getY()),
                        Pointer.groundHeight(head.z), point, 0);
            }
        }
        double height = z.isProne() || z.isOnFloor() ? 0.10 : useHead ? 0.64 : 0.44;
        return Pointer.project(x, y, Pointer.groundHeight(z.getZ()) + height, point, 0);
    }

    private float score(IsoPlayer p, IsoZombie z, float cx, float cy, float x, float y, float span) {
        if (z.isDead() || z.getCurrentSquare() == null || z.getCell() != p.getCell()
                || (int) z.getZ() != (int) p.getZ() || p.DistToSquared(z) > 400
                || !z.getCurrentSquare().isCanSee(p.getIndex())) {
            return Float.POSITIVE_INFINITY;
        }
        if (!Pointer.project(z.getX(), z.getY(), Pointer.groundHeight(z.getZ()), point, 0)) {
            return Float.POSITIVE_INFINITY;
        }
        float dx = point[0] - cx, dy = point[1] - cy;
        float along = dx * x + dy * y;
        float side = Math.abs(dx * y - dy * x);
        if (along <= 0 || along > span * 1.5f || side > along * (z == locked ? 0.24f : 0.16f)) {
            return Float.POSITIVE_INFINITY;
        }
        float score = side / along + 0.16f * Math.abs(along / span - 1);
        if (p.getPrimaryHandItem() instanceof zombie.inventory.types.HandWeapon weapon && weapon.isRanged()
                && !projectTarget(z, z.getX(), z.getY(), assistHead)) {
            return Float.POSITIVE_INFINITY;
        }
        return z == locked ? score - 0.06f : score;
    }
}