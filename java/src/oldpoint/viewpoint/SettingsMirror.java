package oldpoint.viewpoint;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import viewpoint.platform.KeyBind;
import viewpoint.platform.KeyChord;
import viewpoint.platform.Keys;
import viewpoint.platform.LiveSettings;
import viewpoint.platform.SettingsWindow;

/** Viewpoint's Delete menu mirrors the existing PZ ModOptions values. */
final class SettingsMirror {
    private static final Map<String, LiveSettings.Setting> settings = new LinkedHashMap<>();
    private static final Map<String, Double> applied = new LinkedHashMap<>();
    private static final Map<String, List<KeyChord>> appliedKeys = new LinkedHashMap<>();
    private static Method declareKey;
    private static Method setKey;
    private static Method pageName;
    private static Method nativeTabs;
    private static Constructor<?> nativeSubtab;
    private static List<?> tabs = List.of();
    private static boolean reported;

    private SettingsMirror() {}

    static void toggle(String id, String label, String section, String help, boolean value) {
        if (settings.containsKey(id)) {
            return;
        }
        var setting = LiveSettings.toggle("oldpoint." + id, label, section, value);
        setting.describe(help);
        settings.put(id, setting);
        put(id, value ? 1 : 0);
    }

    static void number(String id, String label, String section, String help,
                       double min, double max, double step, double value) {
        if (settings.containsKey(id)) {
            return;
        }
        var setting = LiveSettings.number("oldpoint." + id, label, section,
                (float) min, (float) max, (float) step, (float) value);
        setting.describe(help);
        settings.put(id, setting);
        put(id, value);
    }

    static void choice(String id, String label, String section, String help,
                       String values, String labels, int value) {
        if (settings.containsKey(id)) {
            return;
        }
        var setting = LiveSettings.choice("oldpoint." + id, label, section,
                values.split("\\t", -1), value);
        setting.shownAs(labels.split("\\t", -1));
        setting.describe(help);
        settings.put(id, setting);
        put(id, value);
    }

    static void key(String id, String label, String section, String help, int value) {
        if (settings.containsKey(id)) {
            return;
        }
        try {
            if (declareKey == null) {
                declareKey = Keys.class.getDeclaredMethod("declare", String.class, String.class,
                        String.class, KeyBind.Kind.class, boolean.class, String.class, String.class);
                declareKey.setAccessible(true);
                setKey = KeyBind.class.getDeclaredMethod("set", List.class);
                setKey.setAccessible(true);
            }
            KeyBind.Kind kind = id.equals("cameraRotationKey") ? KeyBind.Kind.HOLD : KeyBind.Kind.PRESS;
            var setting = (KeyBind) declareKey.invoke(null, "oldpoint." + id, label,
                    section, kind, false, "", help);
            settings.put(id, setting);
            put(id, value);
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
        }
    }

    static void put(String id, double value) {
        var setting = settings.get(id);
        if (setting == null || !Double.isFinite(value)) {
            return;
        }
        try {
            if (setting instanceof LiveSettings.Toggle toggle) {
                toggle.set(value != 0);
            } else if (setting instanceof LiveSettings.Number number) {
                number.set((float) value);
            } else if (setting instanceof LiveSettings.Choice choice) {
                choice.set((int) value);
            } else if (setting instanceof KeyBind key) {
                int code = (int) value;
                setKey.invoke(key, code > 0 ? List.of(new KeyChord(code, List.of())) : List.of());
            }
            applied.put(id, get(id));
            if (setting instanceof KeyBind key) appliedKeys.put(id, List.copyOf(key.chords()));
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
        }
    }

    static double get(String id) {
        var setting = settings.get(id);
        if (setting instanceof LiveSettings.Toggle toggle) {
            return toggle.get() ? 1 : 0;
        }
        if (setting instanceof LiveSettings.Number number) {
            return number.get();
        }
        if (setting instanceof LiveSettings.Choice choice) {
            return choice.get();
        }
        if (setting instanceof KeyBind key) {
            return key.chords().isEmpty() ? 0 : key.chords().get(0).key();
        }
        return Double.NaN;
    }

    static boolean changed() {
        if (SettingsWindow.shown()) {
            return false;
        }
        for (var entry : applied.entrySet()) {
            if (Double.compare(entry.getValue(), get(entry.getKey())) != 0) {
                return true;
            }
            if (settings.get(entry.getKey()) instanceof KeyBind key
                    && !key.chords().equals(appliedKeys.get(entry.getKey()))) return true;
        }
        return false;
    }

    static void showWhen(String id, String controller, int value) {
        var setting = settings.get(id);
        if (setting != null && settings.containsKey(controller)) {
            setting.shownWhen(() -> get(controller) == value);
        }
    }

    static void tabs(String labels) {
        try {
            Class<?> pages = Class.forName("viewpoint.platform.SettingsPages");
            Class<?> subtab = Class.forName("viewpoint.platform.SettingsPages$Subtab");
            nativeSubtab = subtab.getDeclaredConstructor(String.class, List.class);
            nativeSubtab.setAccessible(true);
            nativeTabs = pages.getDeclaredMethod("subtabs", String.class, List.class,
                    java.util.Set.class, Map.class, float.class, Runnable.class);
            nativeTabs.setAccessible(true);
            ArrayList<Object> configured = new ArrayList<>();
            for (String label : labels.split("\\t", -1)) {
                configured.add(nativeSubtab.newInstance(label, List.of("OldPoint/" + label)));
            }
            tabs = List.copyOf(configured);
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
        }
    }

    static boolean drawTabs(Object page, Map<?, ?> byTab, Map<?, ?> byPlace, float width) {
        if (nativeTabs == null || tabs.isEmpty()) {
            return false;
        }
        try {
            if (pageName == null) {
                pageName = page.getClass().getMethod("name");
                pageName.setAccessible(true);
            }
            if (!"OldPoint".equals(pageName.invoke(page))) {
                return false;
            }
            nativeTabs.invoke(null, "OldPoint", tabs, byTab.get("OldPoint"), byPlace, width, null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
            return false;
        }
    }

    private static void report(Exception error) {
        if (!reported) {
            reported = true;
            System.out.println("[OldPoint] Viewpoint settings mirror unavailable: " + error);
        }
    }
}
