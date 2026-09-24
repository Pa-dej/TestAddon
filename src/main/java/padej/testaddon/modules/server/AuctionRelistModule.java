package padej.testaddon.modules.server;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;
import padej.testaddon.util.ServerUtil;

import java.util.Locale;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.AuctionRelistModule}.
 *
 * <p><b>Назначение</b>: периодически (раз в {@code intervalSeconds}) запускает
 * последовательность: {@code /ah} → клик по слоту "хранилище" (46) → клик
 * по слоту "перевыставить" (52) → закрыть экран. Используется для FunTime
 * аукциона, чтобы автоматически перевыставлять снятые лоты.</p>
 *
 * <p>Логика — машина состояний с детекцией активного экрана по заголовку
 * ({@code "аукцион"} / {@code "хранилище"}), 1:1 с оригиналом.</p>
 */
public class AuctionRelistModule extends Module {

    private enum State { IDLE, OPENING_AUCTION, CLICK_STORAGE, CLICK_RESELL, CLOSING }

    private static final int STORAGE_BUTTON_SLOT = 46;
    private static final int RESELL_BUTTON_SLOT  = 52;

    private final ValueSetting intervalSeconds = new ValueSetting(
            "auction_relist.interval.name", "auction_relist.interval.desc"
    ).range(30, 3600).setValue(300.0f).setInteger(true);

    private State state = State.IDLE;
    private long lastCycleMs;
    private long stateSinceMs;

    public AuctionRelistModule() {
        super("module.auction_relist.name", SoupBetterCategory.SERVER);
        setup(intervalSeconds);
    }

    @Override
    public void activate()   { state = State.IDLE; lastCycleMs = 0L; stateSinceMs = 0L; }

    @Override
    public void deactivate() { state = State.IDLE; lastCycleMs = 0L; stateSinceMs = 0L; }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (!ServerUtil.isFunTimeServer()) return;

        boolean auctionOpen = isHandledScreenWithTitle("auction", "аукцион");
        boolean storageOpen = isHandledScreenWithTitle("storage", "хранилище");
        long now = System.currentTimeMillis();

        if (!auctionOpen && !storageOpen
                && state != State.IDLE && state != State.OPENING_AUCTION) {
            state = State.IDLE;
        }

        switch (state) {
            case IDLE -> {
                long delayMs = Math.max(30L, (long) intervalSeconds.getValue()) * 1000L;
                if (now - lastCycleMs >= delayMs && mc.currentScreen == null) {
                    mc.getNetworkHandler().sendChatCommand("ah");
                    state = State.OPENING_AUCTION;
                    stateSinceMs = now;
                    lastCycleMs = now;
                }
            }
            case OPENING_AUCTION -> {
                if (auctionOpen && now - stateSinceMs >= 500L) {
                    state = State.CLICK_STORAGE;
                    stateSinceMs = now;
                }
            }
            case CLICK_STORAGE -> {
                if (auctionOpen && now - stateSinceMs >= 300L) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            STORAGE_BUTTON_SLOT, 0, SlotActionType.PICKUP, mc.player);
                    state = State.CLICK_RESELL;
                    stateSinceMs = now;
                }
            }
            case CLICK_RESELL -> {
                if (storageOpen && now - stateSinceMs >= 500L) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            RESELL_BUTTON_SLOT, 0, SlotActionType.PICKUP, mc.player);
                    state = State.CLOSING;
                    stateSinceMs = now;
                }
            }
            case CLOSING -> {
                if (now - stateSinceMs >= 500L) {
                    mc.player.closeHandledScreen();
                    state = State.IDLE;
                }
            }
        }
    }

    private boolean isHandledScreenWithTitle(String... needles) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return false;
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (title.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
