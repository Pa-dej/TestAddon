package padej.testaddon.modules.gameplay;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.testaddon.SoupBetterCategory;

public class ElytraUtilityModule extends Module {

    private static final int CHEST_SLOT_ID = 6;
    private static final long SWAP_DELAY_MS = 120L;

    private final BindSetting key = new BindSetting(
            "elytra.key.name", "elytra.key.desc"
    );

    private long lastActionMs = 0;

    public ElytraUtilityModule() {
        super("module.elytra_utility.name", SoupBetterCategory.GAMEPLAY);
        setup(key);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.player == null || !e.isKeyDown(key.getKey())) return;
        long now = System.currentTimeMillis();
        if (now - lastActionMs < SWAP_DELAY_MS) return;
        lastActionMs = now;
        trySwap();
    }

    private void trySwap() {
        if (mc.player == null || mc.interactionManager == null) return;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean hasElytra = !chest.isEmpty() && chest.isOf(Items.ELYTRA);

        if (hasElytra) {
            // Снять элитру — найти нагрудник в инвентаре
            swapChestTo(Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
        } else {
            // Одеть элитру
            swapChestTo(Items.ELYTRA);
        }
    }

    private void swapChestTo(net.minecraft.item.Item... targets) {
        if (mc.player == null || mc.interactionManager == null) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            for (var t : targets) {
                if (s.isOf(t)) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            i < 9 ? i + 36 : i, // маппинг слота в экран-хендлер
                            0, SlotActionType.SWAP, mc.player
                    );
                    return;
                }
            }
        }
    }
}
