package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

/** Verify both Build 42 square-object collection shapes without loading a world. */
public final class SquareObjectsTest {
    public static final class LegacyObjects {
        public int size() {
            return 1;
        }

        public IsoObject get(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException(index);
            }
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!List.class.isAssignableFrom(IsoGridSquare.class.getMethod("getObjects").getReturnType())) {
            throw new AssertionError("Expected the Build 42.22 List signature");
        }
        List<IsoObject> current = new ArrayList<>();
        if (!SquareObjects.isEmpty(current)) {
            throw new AssertionError("Empty List was not empty");
        }
        current.add(null);
        if (SquareObjects.size(current) != 1 || SquareObjects.get(current, 0) != null) {
            throw new AssertionError("List indexed access failed");
        }
        Object legacy = new LegacyObjects();
        if (SquareObjects.isEmpty(legacy)
                || SquareObjects.size(legacy) != 1
                || SquareObjects.get(legacy, 0) != null) {
            throw new AssertionError("Legacy indexed access failed");
        }
        System.out.println("PASS: Build 42.22 List and legacy indexed square objects");
    }
}
