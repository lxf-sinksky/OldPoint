package oldpoint.viewpoint;

import java.nio.IntBuffer;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import viewpoint.platform.GlProgram;

/**
 * Hands the light pass the player's line of sight as a uniform bit array. A texture would need a sampler unit, and
 * the heavier shader packs' light passes already use every one the driver allows.
 */
final class SightBits {
    private static final String NAME = "uOPVisionBits";
    private static final Map<GlProgram, int[]> locations = new WeakHashMap<>();
    private static final IntBuffer buffer = BufferUtils.createIntBuffer(VisionSystem.SIGHT_SIZE * VisionSystem.SIGHT_SIZE / 32);
    private static boolean failed;

    private SightBits() {}

    static boolean set(GlProgram program, VisionSystem.LineOfSight sight) {
        if (failed) {
            return false;
        }
        try {
            int[] cached = locations.get(program);
            if (cached == null || cached[0] != program.id()) {
                cached = new int[] {program.id(), GL20.glGetUniformLocation(program.id(), NAME + "[0]")};
                locations.put(program, cached);
            }
            if (cached[1] < 0) {
                return false;
            }
            buffer.clear();
            buffer.put(sight.bits()).flip();
            GL30.glUniform4uiv(cached[1], buffer);
            return true;
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            System.out.println("[OldPoint] line-of-sight shading disabled: " + error);
            return false;
        }
    }
}
