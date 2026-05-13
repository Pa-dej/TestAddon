package padej.testaddon.modules.combat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.testaddon.SoupBetterCategory;

public class ItemHighlighterModule extends Module {

    private final BooleanSetting totems = new BooleanSetting(
            "item_hl.totems.name", "item_hl.totems.desc"
    ).setValue(true);

    private final BooleanSetting potions = new BooleanSetting(
            "item_hl.potions.name", "item_hl.potions.desc"
    ).setValue(true);

    private final BooleanSetting netheriteGear = new BooleanSetting(
            "item_hl.netherite.name", "item_hl.netherite.desc"
    ).setValue(false);

    private final ColorSetting totemColor = new ColorSetting(
            "item_hl.totem_color.name", "item_hl.totem_color.desc"
    ).value(0xAAFFD700).visible(totems::isValue);

    private final ColorSetting potionColor = new ColorSetting(
            "item_hl.potion_color.name", "item_hl.potion_color.desc"
    ).value(0xAA7B1FA2).visible(potions::isValue);

    private final ColorSetting netheriteColor = new ColorSetting(
            "item_hl.netherite_color.name", "item_hl.netherite_color.desc"
    ).value(0xAA607D8B).visible(netheriteGear::isValue);

    private static ItemHighlighterModule INSTANCE;

    public ItemHighlighterModule() {
        super("module.item_highlighter.name", SoupBetterCategory.COMBAT);
        setup(totems, totemColor, potions, potionColor, netheriteGear, netheriteColor);
        INSTANCE = this;
    }

    /** Вызывается из HandledScreenMixin для получения цвета подсветки слота. */
    public int getSlotHighlightColor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (totems.isValue() && stack.isOf(Items.TOTEM_OF_UNDYING))
            return totemColor.getColor();
        if (potions.isValue() && (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION)))
            return potionColor.getColor();
        if (netheriteGear.isValue() && isNetheriteGear(stack))
            return netheriteColor.getColor();
        return 0;
    }

    private boolean isNetheriteGear(ItemStack s) {
        return s.isOf(Items.NETHERITE_SWORD) || s.isOf(Items.NETHERITE_AXE)
                || s.isOf(Items.NETHERITE_HELMET) || s.isOf(Items.NETHERITE_CHESTPLATE)
                || s.isOf(Items.NETHERITE_LEGGINGS) || s.isOf(Items.NETHERITE_BOOTS);
    }

    public static ItemHighlighterModule getInstance() { return INSTANCE; }
}
