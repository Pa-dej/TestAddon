package padej.testaddon.modules.gameplay;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.testaddon.SoupBetterCategory;

public class AutoSwapModule extends Module {

    private static final String SPHERE_KEY    = "сфера";
    private static final String TALISMAN_KEY  = "талисман";

    private final SelectSetting mode = new SelectSetting(
            "auto_swap.mode.name", "auto_swap.mode.desc"
    ).value("menu", "direct").selected("menu");

    private final TextSetting sphereName = new TextSetting(
            "auto_swap.sphere.name", "auto_swap.sphere.desc"
    ).setText(SPHERE_KEY);

    private final TextSetting talismanName = new TextSetting(
            "auto_swap.talisman.name", "auto_swap.talisman.desc"
    ).setText(TALISMAN_KEY);

    private final BindSetting key = new BindSetting(
            "auto_swap.key.name", "auto_swap.key.desc"
    );

    private long lastSwapMs = 0;

    public AutoSwapModule() {
        super("module.auto_swap.name", SoupBetterCategory.GAMEPLAY);
        setup(mode, sphereName, talismanName, key);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.player == null || !e.isKeyDown(key.getKey())) return;
        long now = System.currentTimeMillis();
        if (now - lastSwapMs < 300) return;
        lastSwapMs = now;
        trySwap();
    }

    private void trySwap() {
        if (mc.player == null || mc.interactionManager == null) return;
        var inv = mc.player.getInventory();
        String target = sphereName.getText().toLowerCase();

        // Ищем предмет в инвентаре (слоты 9–35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            String name = stack.getName().getString().toLowerCase();
            if (name.contains(target)) {
                if (mode.isSelected("direct")) {
                    // Прямой своп: hotbar slot 0 ↔ inventory slot i
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            i, 0, SlotActionType.SWAP, mc.player
                    );
                } else {
                    // Через меню: открыть инвентарь
                    mc.setScreen(new InventoryScreen(mc.player));
                }
                return;
            }
        }
    }
}
