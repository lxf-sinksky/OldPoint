package oldpoint.viewpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatchers;

/** Exercise ZombieBuddy's actual strict matcher, not just ByteBuddy advice inlining by method name. */
public final class RoofPatchMatchingTest {
    public static void main(String[] args) throws Exception {
        check(GeneratedRoofs.RoofPatch.class, "viewpoint.world.RoofCook");
        check(GeneratedRoofs.GablePatch.class, "viewpoint.world.RoofGables");
    }

    private static void check(Class<?> patchClass, String targetName) throws Exception {
        var patch = patchClass.getAnnotation(Patch.class);
        var advice = patchClass.getDeclaredMethods()[0];
        Map<Integer, Class<?>> arguments = new HashMap<>();
        var annotations = advice.getParameterAnnotations();
        var types = advice.getParameterTypes();
        for (int i = 0; i < annotations.length; i++) {
            for (var annotation : annotations[i]) {
                if (annotation instanceof Patch.Argument argument) {
                    arguments.put(argument.value(), types[i]);
                }
            }
        }
        Class<?> matcherClass = Class.forName("me.zed_0xff.zombie_buddy.PatchEngine$2");
        var constructor = matcherClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        boolean noParameters = arguments.isEmpty();
        Object matcher = constructor.newInstance(noParameters, patch.strictMatch(),
                noParameters ? List.of() : List.of(arguments),
                noParameters ? List.of() : List.of(true), arguments.size());
        var target = TypeDescription.ForLoadedType.of(Class.forName(targetName)).getDeclaredMethods()
                .filter(ElementMatchers.named("vertices")).getOnly();
        var matches = matcherClass.getDeclaredMethod(
                "matches", net.bytebuddy.description.method.MethodDescription.class);
        matches.setAccessible(true);
        boolean accepted = (boolean) matches.invoke(matcher, target);
        System.out.println((accepted ? "PASS " : "FAIL ") + targetName + ".vertices args="
                + target.getParameters().size() + ", advice args=" + arguments.size()
                + ", strict=" + patch.strictMatch());
        if (!accepted) {
            throw new AssertionError("ZombieBuddy rejects the roof provenance hook before it can tag generated faces");
        }
    }
}
