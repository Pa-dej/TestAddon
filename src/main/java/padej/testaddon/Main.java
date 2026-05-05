package padej.testaddon;

import net.fabricmc.api.ModInitializer;
import padej.soup.api.SoupAPI;
import padej.soup.api.addon.SoupAddon;

public class Main implements ModInitializer, SoupAddon {

    @Override
    public void onInitialize() {
        // Вызывается Fabric — здесь ничего не делаем.
        // Вся инициализация аддона — в onInitialize(SoupAPI).
    }

    @Override
    public String getId() {
        return "test-addon";
    }

    @Override
    public String getName() {
        return "Test Addon";
    }

    @Override
    public String getVersion() {
        return "1.21.4+1.0.0";
    }

    @Override
    public void onInitialize(SoupAPI api) {
        // 1. Переводы — регистрируй первыми (до модулей)
        api.getLocalizationManager().registerLanguage("en_us",
                getClass().getResourceAsStream("/lang/en_us.ini"));
        api.getLocalizationManager().registerLanguage("ru_ru",
                getClass().getResourceAsStream("/lang/ru_ru.ini"));

        // 2. Модули
        api.getModuleRepository().register(new MyModule());

        // 3. HUD-элементы
        api.getDraggableRepository().register(new MyHudElement());
    }
}
