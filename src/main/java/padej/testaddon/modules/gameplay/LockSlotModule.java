package padej.testaddon.modules.gameplay;

import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.HotBarScrollEvent;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.notification.NotificationService;
import padej.testaddon.SoupBetterCategory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LockSlotModule extends Module {

    private static LockSlotModule INSTANCE;

    private final BindSetting toggleKey = new BindSetting(
            "lock_slot.toggle.name", "lock_slot.toggle.desc"
    );

    private final BooleanSetting blockScroll = new BooleanSetting(
            "lock_slot.block_scroll.name", "lock_slot.block_scroll.desc"
    ).setValue(true);

    private final BooleanSetting notify = new BooleanSetting(
            "lock_slot.notify.name", "lock_slot.notify.desc"
    ).setValue(true);

    private final Set<Integer> lockedSlots = new HashSet<>();

    public LockSlotModule() {
        super("module.lock_slot.name", SoupBetterCategory.GAMEPLAY);
        setup(toggleKey, blockScroll, notify);
        INSTANCE = this;
    }

    /**
     * Переключает блокировку слота. Допустимые значения slot:
     * 0..8 — слоты хотбара, 40 — offhand (как в оригинале).
     */
    public void toggleSlot(int slot) {
        if (slot < 0 || (slot > 8 && slot != 40)) return;
        boolean nowLocked;
        if (lockedSlots.remove(slot)) {
            nowLocked = false;
        } else {
            lockedSlots.add(slot);
            nowLocked = true;
        }
        if (notify.isValue() && mc.player != null) {
            String msg = (nowLocked ? "§aЗаблокирован слот " : "§cРазблокирован слот ") + (slot + 1);
            NotificationService ns = NotificationService.getInstance();
            if (ns != null) {
                ns.show(msg, 1500);
            } else {
                mc.player.sendMessage(Text.literal(msg), true);
            }
        }
    }

    public boolean isLocked(int slot) { return lockedSlots.contains(slot); }

    public Set<Integer> getLockedSlots() { return Collections.unmodifiableSet(lockedSlots); }

    @EventHandler
    public void onKey(KeyEvent e) {
        int k = toggleKey.getKey();
        if (k == GLFW.GLFW_KEY_UNKNOWN || mc.player == null) return;
        if (!e.isKeyDown(k)) return;
        toggleSlot(mc.player.getInventory().selectedSlot);
    }

    @EventHandler
    public void onScroll(HotBarScrollEvent e) {
        if (!blockScroll.isValue() || mc.player == null) return;
        int cur = mc.player.getInventory().selectedSlot;
        if (lockedSlots.contains(cur)) e.cancel();
    }

    /**
     * Вызывается из LockSlotMixin для блокировки кликов по слотам.
     * @param slotId ID слота в контейнере (0-based, зависит от типа контейнера)
     */
    public boolean shouldBlockSlotClick(int slotId) {
        if (!isEnabled()) return false;
        // Hotbar в экране инвентаря: слоты 36–44
        int hotbarFromInventory = slotId - 36;
        if (hotbarFromInventory >= 0 && hotbarFromInventory <= 8)
            return lockedSlots.contains(hotbarFromInventory);
        // Прямой доступ к хотбару (0–8) в некоторых контекстах
        if (slotId >= 0 && slotId <= 8)
            return lockedSlots.contains(slotId);
        // Offhand: в playerScreenHandler — слот 45, оригинал кодирует как 40.
        if (slotId == 45 || slotId == 40)
            return lockedSlots.contains(40);
        return false;
    }

    public boolean isOffhandLocked() { return lockedSlots.contains(40); }

    public static LockSlotModule getInstance() { return INSTANCE; }
}
