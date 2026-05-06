package padej.testaddon;

import padej.soup.api.feature.module.Category;
import padej.soup.api.feature.module.CustomCategory;

/**
 * Категории TestAddon.
 *
 * <p>Создаётся один раз как статическая константа. Все модули аддона
 * передают {@link #MAIN} в конструктор {@code super(..., TestAddonCategory.MAIN)}.</p>
 */
public final class TestAddonCategory {

    /**
     * Главная (и единственная) категория TestAddon.
     * Отображается в меню как отдельная вкладка рядом со встроенными категориями.
     */
    public static final Category MAIN = new CustomCategory(
            "test_addon",               // уникальный идентификатор
            "category.test_addon"       // ключ локализации
    );

    private TestAddonCategory() {}
}
