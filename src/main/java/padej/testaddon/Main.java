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
        // 1. Переводы — регистрируй первыми (до модулей),
        //    чтобы названия категорий и модулей сразу локализовались.
        // Файлы в /lang/test_addon/, а не в /lang/ — иначе на общем classpath (dev-окружение)
        // они бы перекрывали /lang/en_us.ini основного мода и ломали все его переводы.
        api.getLocalizationManager().registerLanguage("en_us",
                getClass().getResourceAsStream("/lang/test_addon/en_us.ini"));
        api.getLocalizationManager().registerLanguage("ru_ru",
                getClass().getResourceAsStream("/lang/test_addon/ru_ru.ini"));

        // 2. Модули — все используют TestAddonCategory.MAIN,
        //    поэтому появятся в отдельной вкладке «TestAddon» в меню.
        api.getModuleRepository().register(
                new MyModule(),
                new HudInfoModule()
        );

        // 3. HUD-элементы
        api.getDraggableRepository().register(new MyHudElement());
    }
}
