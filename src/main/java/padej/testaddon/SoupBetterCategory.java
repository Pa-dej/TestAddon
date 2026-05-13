package padej.testaddon;

import padej.soup.api.feature.module.Category;
import padej.soup.api.feature.module.CustomCategory;

/**
 * Категории аддона SoupBetter.
 *
 * <p>Каждый идентификатор соответствует файлу иконки:
 * {@code assets/minecraft/textures/modules/{identifier}.png}</p>
 */
public final class SoupBetterCategory {

    public static final Category GAMEPLAY = new CustomCategory(
            "gameplay",
            "category.soup_better.gameplay"
    );

    public static final Category COMBAT = new CustomCategory(
            "combat",
            "category.soup_better.combat"
    );

    public static final Category SERVER = new CustomCategory(
            "server",
            "category.soup_better.server"
    );

    private SoupBetterCategory() {}
}
