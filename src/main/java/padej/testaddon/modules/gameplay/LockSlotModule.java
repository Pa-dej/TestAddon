package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.HotBarScrollEvent;
import padej.soup.api.feature.module.Module;
import padej.testaddon.SoupBetterCategory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LockSlotModule extends Module {

    private static LockSlotModule INSTANCE;

    private final Set<Integer> lockedSlots = new HashSet<>();

    public LockSlotModule() {
        super("module.lock_slot.name", SoupBetterCategory.GAMEPLAY);
        setup();
        INSTANCE = this;
    }

    public void toggleSlot(int slot) {
        if (!lockedSlots.remove(slot)) lockedSlots.add(slot);
    }

    public boolean isLocked(int slot) { return lockedSlots.contains(slot); }

    public Set<Integer> getLockedSlots() { return Collections.unmodifiableSet(lockedSlots); }

    @EventHandler
    public void onScroll(HotBarScrollEvent e) {
        if (mc.player == null) return;
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
        return false;
    }

    public static LockSlotModule getInstance() { return INSTANCE; }
}
