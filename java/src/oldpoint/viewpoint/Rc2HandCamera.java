package oldpoint.viewpoint;

public final class Rc2HandCamera {
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    public static void enter() {
        depth.set(depth.get() + 1);
    }

    public static void exit() {
        int n = depth.get() - 1;
        if (n <= 0) {
            depth.remove();
        } else {
            depth.set(n);
        }
    }

    public static boolean active() {
        return depth.get() != 0;
    }

    private Rc2HandCamera() {
    }
}
