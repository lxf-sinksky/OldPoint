package oldpoint.viewpoint;

import java.util.HashSet;
import java.util.Set;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import viewpoint.core.Frame;
import viewpoint.render.TorchDraws;
import zombie.Lua.LuaManager;
import zombie.characters.AttachedItems.AttachedItems;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoLightSource;
import zombie.iso.Vector3;

/** Adapts Authentic Z carried glowsticks to Viewpoint's nightly torch snapshot. */
public final class Rc2AuthenticGlowsticks {
    private static final Set<Integer> carriedIds = new HashSet<>();
    private static final Vector3 hand = new Vector3();

    private Rc2AuthenticGlowsticks() {}

    private static KahluaTable bridge() {
        Object value = LuaManager.env == null ? null : LuaManager.env.rawget("OldPointAuthenticZCarriedLightsV1");
        return value instanceof KahluaTable table ? table : null;
    }

    public static void refresh() {
        carriedIds.clear();
        KahluaTable table = bridge();
        if (table == null) {
            return;
        }
        KahluaTableIterator iterator = table.iterator();
        while (iterator.advance()) {
            if (!(iterator.getKey() instanceof IsoLightSource source)
                    || source.id == 0 || !(iterator.getValue() instanceof KahluaTable)) {
                continue;
            }
            carriedIds.add(source.id);
        }
    }

    public static boolean replaces(int id) {
        return carriedIds.contains(id);
    }

    public static boolean glowstick(InventoryItem item) {
        if (item == null) {
            return false;
        }
        return switch (item.getType()) {
            case "AuthenticGlowstick_Green_On", "AuthenticGlowstick_Blue_On", "AuthenticGlowstick_Yellow_On",
                    "AuthenticGlowstick_Red_On", "AuthenticGlowstick_Orange_On", "AuthenticGlowstick_White_On",
                    "AuthenticGlowstick_Purple_On", "AuthenticGlowstick_Pink_On" -> true;
            default -> false;
        };
    }

    private static boolean equipped(IsoPlayer player, InventoryItem item) {
        if (player.getPrimaryHandItem() == item || player.getSecondaryHandItem() == item) {
            return true;
        }
        AttachedItems attached = player.getAttachedItems();
        for (int index = 0; attached != null && index < attached.size(); index++) {
            if (attached.getItemByIndex(index) == item) {
                return true;
            }
        }
        return false;
    }

    public static void snapshot(Frame frame) {
        KahluaTable table = bridge();
        if (table == null) {
            return;
        }
        TorchDraws torches = frame.scene.torches;
        KahluaTableIterator iterator = table.iterator();
        while (torches.count < 8 && iterator.advance()) {
            if (!(iterator.getKey() instanceof IsoLightSource source)
                    || !source.active || !(iterator.getValue() instanceof KahluaTable carried)
                    || !(carried.rawget("player") instanceof IsoPlayer player) || player.isDead()
                    || !(carried.rawget("item") instanceof InventoryItem item)
                    || !glowstick(item) || !equipped(player, item)) {
                continue;
            }
            float x = player.getX();
            float y = player.getY();
            float z = player.getZ();
            float dx = x - frame.camX;
            float dy = y - frame.camY;
            if (dx * dx + dy * dy > 961.0f) {
                continue;
            }
            float handHeight = 1.1f;
            int modelIndex = -1;
            int boneIndex = -1;
            if (player.hasActiveModel()) {
                String bone = player.getPrimaryHandItem() == item ? "Bip01_Prop1"
                        : player.getSecondaryHandItem() == item ? "Bip01_Prop2" : "Bip01_Spine1";
                boneIndex = player.getAnimationPlayer().getSkinningBoneIndex(bone, -1);
                modelIndex = frame.scene.models.find(player.getModelInstance());
                if (boneIndex >= 0) {
                    Model.boneToWorldCoords((IsoGameCharacter) player, boneIndex, hand);
                    x = hand.x;
                    y = hand.y;
                    z = hand.z;
                    handHeight = 0.0f;
                }
            }
            float relief = frame.scene.relief == null ? 0.0f
                    : frame.scene.relief.scene(player.getX(), player.getY());
            append(torches, -(x - frame.camX), (z - frame.camZ) * 2.4494896f + handHeight + relief,
                    -(y - frame.camY), source.radius, channel(source.r), channel(source.g), channel(source.b),
                    modelIndex, boneIndex);
        }
    }

    private static float channel(float value) {
        return Math.max(0.0f, Math.min(1.0f, value * 2.0f));
    }

    public static boolean append(TorchDraws torches, float x, float y, float z, float radius,
                                 float red, float green, float blue, int modelIndex, int boneIndex) {
        if (torches.count >= 8) {
            return false;
        }
        int index = torches.count++;
        int offset = index * 11;
        float[] records = torches.records;
        records[offset] = x;
        records[offset + 1] = y;
        records[offset + 2] = z;
        records[offset + 3] = radius;
        records[offset + 4] = 0.0f;
        records[offset + 5] = 0.0f;
        records[offset + 6] = 0.0f;
        records[offset + 7] = -1.0f;
        records[offset + 8] = red;
        records[offset + 9] = green;
        records[offset + 10] = blue;
        torches.rides[index * 2] = modelIndex;
        torches.rides[index * 2 + 1] = boneIndex;
        return true;
    }
}
