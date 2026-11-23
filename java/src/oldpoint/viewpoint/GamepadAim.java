package oldpoint.viewpoint;

import zombie.characters.CharacterInputMode;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.Vector2;

/** Resolve the mapped aiming stick in screen space against the active overhead projection. */
final class GamepadAim {
    static volatile float gain = 1;
    static volatile boolean assist = true;
    private static final ThreadLocal<GamepadAim> local = ThreadLocal.withInitial(GamepadAim::new);
    private final float[] point = new float[2];
    private IsoZombie locked;
    private long nextSearch;

    static boolean bound(IsoGameCharacter actor) {
        return actor instanceof IsoPlayer p && p.isLocalPlayer() && p.getInputMode() == CharacterInputMode.GAMEPAD;
    }

    static boolean cursor(IsoPlayer p, int height, Vector2 out) {
        return local.get().resolve(p, height, out);
    }

    private boolean resolve(IsoPlayer p, int height, Vector2 out) {
        p.getCharacterInputComponent().getJoypadAimVector(out);
        float x = out.x, y = out.y;
        float length = out.getLength();
        if (length < 0.001f || !Pointer.project(p.getX(), p.getY(),
                Pointer.groundHeight(p.getZ()), point, 0)) {
            locked = null;
            nextSearch = 0;
            return false;
        }
        float cx = point[0], cy = point[1];
        float span = height * 0.32f * Math.min(1.45f, length * gain);
        x /= length;
        y /= length;
        out.set(cx + x * span, cy + y * span);
        if (!assist || p.getCell() == null || !p.isAiming()) {
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
                && !Pointer.project(z.getX(), z.getY(), Pointer.groundHeight(z.getZ())
                        + (z.isProne() ? 0.10 : 0.44), point, 0)) {
            return Float.POSITIVE_INFINITY;
        }
        return z == locked ? score - 0.06f : score;
    }
}