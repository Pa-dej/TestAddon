package padej.testaddon.modules.gameplay;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.testaddon.SoupBetterCategory;
import padej.testaddon.util.ServerUtil;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.ElytraUtilityModule}.
 *
 * <p><b>Назначение</b>: по горячей клавише меняет нагрудник на элитру (или
 * наоборот). На FunTime обычный shift-click по броне не работает, поэтому
 * используется тройной клик: открывается inventory screen, далее
 * pickup(source) → pickup(chest_slot=6) → pickup(source) — это вынимает
 * текущий чест и кладёт source на его место.</p>
 *
 * <p>Поиск нагрудника при снятии элитры — по {@link EquippableComponent}
 * с {@link EquipmentSlot#CHEST}, не привязан к конкретному предмету.</p>
 */
public class ElytraUtilityModule extends Module {

    private static final int CHEST_SLOT_ID = 6;
    private static final long SWAP_DELAY_MS = 120L;

    private final BindSetting key = new BindSetting(
            "elytra.key.name", "elytra.key.desc"
    ).setKey(GLFW.GLFW_KEY_R);

    private boolean swapping;
    private long lastActionMs;

    public ElytraUtilityModule() {
        super("module.elytra_utility.name", SoupBetterCategory.GAMEPLAY);
        setup(key);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        int k = key.getKey();
        if (k == GLFW.GLFW_KEY_UNKNOWN || mc.player == null) return;
        if (mc.currentScreen != null) return;
        if (!ServerUtil.isFunTimeServer()) return;
        if (!e.isKeyDown(k)) return;
        beginInventorySwap();
    }

    private void beginInventorySwap() {
        if (mc.player == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        if (swapping || now - lastActionMs < 200L) return;

        ItemStack chestStack = mc.player.playerScreenHandler.getSlot(CHEST_SLOT_ID).getStack();
        boolean elytraEquipped = chestStack.isOf(Items.ELYTRA);
        int sourceSlotId = elytraEquipped ? findChestplateSlotId() : findElytraSlotId();
        if (sourceSlotId == -1) return;

        swapping = true;
        lastActionMs = now;
        mc.setScreen(new InventoryScreen(mc.player));

        new Thread(() -> {
            try {
                Thread.sleep(SWAP_DELAY_MS);
                mc.execute(() -> performSwap(sourceSlotId));
                Thread.sleep(SWAP_DELAY_MS + 60L);
                mc.execute(this::finishSwap);
            } catch (InterruptedException ignored) {
                mc.execute(this::finishSwap);
            }
        }, "SoupBetter-ElytraSwap").start();
    }

    private void performSwap(int sourceSlotId) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (sourceSlotId < 0 || sourceSlotId >= mc.player.playerScreenHandler.slots.size()) return;
        int syncId = mc.player.playerScreenHandler.syncId;

        mc.interactionManager.clickSlot(syncId, sourceSlotId,   0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, CHEST_SLOT_ID,  0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, sourceSlotId,   0, SlotActionType.PICKUP, mc.player);
    }

    private void finishSwap() {
        if (mc.player != null) mc.player.closeHandledScreen();
        mc.setScreen(null);
        swapping = false;
    }

    private int findElytraSlotId() {
        for (int slotId = 9; slotId <= 44; slotId++) {
            if (mc.player.playerScreenHandler.getSlot(slotId).getStack().isOf(Items.ELYTRA)) {
                return slotId;
            }
        }
        return -1;
    }

    private int findChestplateSlotId() {
        for (int slotId = 9; slotId <= 44; slotId++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slotId).getStack();
            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.isOf(Items.ELYTRA)) {
                return slotId;
            }
        }
        return -1;
    }
}
