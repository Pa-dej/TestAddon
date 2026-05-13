package padej.testaddon.modules.server;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.SlotActionType;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.container.SetScreenEvent;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

public class AuctionRelistModule extends Module {

    private final ValueSetting delayMs = new ValueSetting(
            "auction_relist.delay.name", "auction_relist.delay.desc"
    ).range(100, 2000).setValue(300.0f);

    private boolean waitingForScreen = false;
    private long nextActionMs = 0;
    private int step = 0;

    public AuctionRelistModule() {
        super("module.auction_relist.name", SoupBetterCategory.SERVER);
        setup(delayMs);
    }

    @Override
    public void activate() { step = 0; waitingForScreen = false; }

    @Override
    public void deactivate() { step = 0; waitingForScreen = false; }

    @EventHandler
    public void onSetScreen(SetScreenEvent e) {
        if (e.getScreen() instanceof GenericContainerScreen) {
            waitingForScreen = false;
            nextActionMs = System.currentTimeMillis() + (long) delayMs.getValue();
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (System.currentTimeMillis() < nextActionMs) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        // Простейший авто-рилист: клик по первому слоту → подтверждение
        int syncId = screen.getScreenHandler().syncId;
        switch (step) {
            case 0 -> {
                mc.interactionManager.clickSlot(syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                step = 1;
                nextActionMs = System.currentTimeMillis() + (long) delayMs.getValue();
            }
            case 1 -> {
                // Подтверждение (слот кнопки "ОК", обычно последний)
                int slots = screen.getScreenHandler().slots.size();
                mc.interactionManager.clickSlot(syncId, slots - 1, 0, SlotActionType.PICKUP, mc.player);
                step = 0;
                setState(false); // выключаемся после одного цикла
            }
        }
    }
}
