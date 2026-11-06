package oldpoint.viewpoint;

import java.lang.reflect.Field;
import viewpoint.platform.LiveSettings;

public final class Rc2LootSetting {
    public static boolean enabled(Field field, Object object) throws IllegalAccessException {
        return ((LiveSettings.Toggle)field.get(object)).get();
    }
}
