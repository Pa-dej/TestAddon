package padej.testaddon;

import net.fabricmc.api.ModInitializer;
import padej.soup.api.SoupAPI;
import padej.soup.api.addon.SoupAddon;
import padej.testaddon.modules.combat.*;
import padej.testaddon.modules.gameplay.*;
import padej.testaddon.modules.server.*;

public class Main implements ModInitializer, SoupAddon {

    @Override
    public void onInitialize() {
        // Вызывается Fabric — вся инициализация аддона в onInitialize(SoupAPI).
    }

    @Override
    public String getId() { return "soup-better"; }

    @Override
    public String getName() { return "SoupBetter"; }

    @Override
    public String getVersion() { return "1.21.4+1.0.0"; }

    @Override
    public void onInitialize(SoupAPI api) {
        // 1. Переводы — до регистрации модулей
        api.getLocalizationManager().registerLanguage("en_us",
                getClass().getResourceAsStream("/lang/test_addon/en_us.ini"));
        api.getLocalizationManager().registerLanguage("ru_ru",
                getClass().getResourceAsStream("/lang/test_addon/ru_ru.ini"));

        // 2. Модули — Gameplay
        api.getModuleRepository().register(
                new AutoSprintModule(),
                new ZoomModule(),
                new FreelookModule(),
                new LockSlotModule(),
                new AutoSwapModule(),
                new ElytraUtilityModule(),
                new MouseClickerModule(),
                new ShiftTapModule(),
                new CoordinateHelperModule(),
                new LowArmorNotificationsModule(),
                new PickaxeNotificationsModule(),
                new PotionNotificationsModule()
        );

        // 3. Модули — Combat
        api.getModuleRepository().register(
                new TrapTimerModule(),
                new TotemTrackerModule(),
                new PvpAiModule(),
                new PredictionsModule(),
                new SnowballTrackerModule(),
                new ItemHighlighterModule()
        );

        // 4. Модули — Server
        api.getModuleRepository().register(
                new AutoNearModule(),
                new AMHelperModule(),
                new FTHelperModule(),
                new AuctionHelperModule(),
                new AuctionRelistModule(),
                new EventsModule(),
                new EventDelayModule()
        );
    }
}
