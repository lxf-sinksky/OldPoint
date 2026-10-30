package oldpoint.viewpoint;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/** Check actual installed shader material entry points, including the paths which previously bypassed roof tint. */
public final class RoofShaderOutputTest {
    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (String path : List.of("gbuffer.frag", "forward.frag", "far_shell.frag",
                "iris/mesh_fragment.glsl", "iris/far_shell_fragment.glsl")) {
            String result = OcclusionShaders.shader(path, source(path));
            int calls = result.split("oldPointRoofColor\\(", -1).length - 2;
            System.out.println((calls > 0 ? "PASS " : "FAIL ") + path + " roof material tint calls=" + calls);
            if (calls == 0) {
                failures++;
            }
            if (path.startsWith("iris/")) {
                for (String entry : List.of("vp_albedo", "vp_albedoBias", "vp_albedoLod",
                        "vp_albedoGrad", "vp_albedoFetch")) {
                    var pattern = Pattern.compile("vec4 " + entry + "\\([^)]*\\)\\s*\\{([^}]+)}");
                    var matcher = pattern.matcher(result);
                    if (!matcher.find() || !matcher.group(1).contains("oldPointRoofColor(")) {
                        System.out.println("FAIL " + path + " material entry bypasses roof tint: " + entry);
                        failures++;
                    }
                }
            }
        }
        if (failures > 0) {
            throw new AssertionError("Roof tint missing at " + failures + " shader checks");
        }
    }

    private static String source(String path) throws Exception {
        for (String root : List.of("/viewpoint/shaders/", "/viewpoint/shaderpacks/default/shaders/")) {
            try (var stream = RoofShaderOutputTest.class.getResourceAsStream(root + path)) {
                if (stream != null) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("Missing real shader: " + path);
    }
}
