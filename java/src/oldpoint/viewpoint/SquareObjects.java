package oldpoint.viewpoint;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

/** Indexed square-object access across Build 42's PZArrayList and List return signatures. */
final class SquareObjects {
    private record Accessors(Class<?> type, MethodHandle size, MethodHandle get) {}

    private static final MethodHandle OBJECTS = findObjects();
    private static volatile Accessors legacy;

    private SquareObjects() {}

    private static MethodHandle findObjects() {
        try {
            Method method = IsoGridSquare.class.getMethod("getObjects");
            return MethodHandles.lookup().unreflect(method)
                    .asType(MethodType.methodType(Object.class, IsoGridSquare.class));
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    static Object of(IsoGridSquare square) {
        try {
            return (Object) OBJECTS.invokeExact(square);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot read square objects", error);
        }
    }

    static int size(Object objects) {
        if (objects instanceof List<?> list) {
            return list.size();
        }
        try {
            return (int) accessors(objects).size.invokeExact(objects);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot read square object count", error);
        }
    }

    static IsoObject get(Object objects, int index) {
        if (objects instanceof List<?> list) {
            return (IsoObject) list.get(index);
        }
        try {
            return (IsoObject) accessors(objects).get.invokeExact(objects, index);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot read square object", error);
        }
    }

    static boolean isEmpty(Object objects) {
        return size(objects) == 0;
    }

    private static Accessors accessors(Object objects) {
        Accessors cached = legacy;
        if (cached != null && cached.type.isInstance(objects)) {
            return cached;
        }
        synchronized (SquareObjects.class) {
            cached = legacy;
            if (cached != null && cached.type.isInstance(objects)) {
                return cached;
            }
            Class<?> type = objects.getClass();
            try {
                MethodHandle size = MethodHandles.lookup().unreflect(type.getMethod("size"))
                        .asType(MethodType.methodType(int.class, Object.class));
                MethodHandle get = MethodHandles.lookup().unreflect(type.getMethod("get", int.class))
                        .asType(MethodType.methodType(IsoObject.class, Object.class, int.class));
                cached = new Accessors(type, size, get);
                legacy = cached;
                return cached;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported square object list: " + type.getName(), error);
            }
        }
    }
}
