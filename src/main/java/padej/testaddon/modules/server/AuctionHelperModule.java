package padej.testaddon.modules.server;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.testaddon.SoupBetterCategory;

public class AuctionHelperModule extends Module {

    public enum FilterMode {
        ALL, NETHERITE_ARMOR, DIAMOND_ARMOR, NETHERITE_SWORD, DIAMOND_SWORD,
        TOTEMS, APPLES, POTIONS
    }

    private final SelectSetting filterMode = new SelectSetting(
            "auction.filter.name", "auction.filter.desc"
    ).value("all", "netherite_armor", "diamond_armor", "netherite_sword",
            "diamond_sword", "totems", "apples", "potions")
     .selected("all");

    private final BooleanSetting groupByItem = new BooleanSetting(
            "auction.group.name", "auction.group.desc"
    ).setValue(false);

    private final BooleanSetting highlight3Slots = new BooleanSetting(
            "auction.highlight3.name", "auction.highlight3.desc"
    ).setValue(true);

    private final ColorSetting highlightColor = new ColorSetting(
            "auction.hl_color.name", "auction.hl_color.desc"
    ).value(0xAAFFD700);

    private static AuctionHelperModule INSTANCE;

    public AuctionHelperModule() {
        super("module.auction_helper.name", SoupBetterCategory.SERVER);
        setup(filterMode, groupByItem, highlight3Slots, highlightColor);
        INSTANCE = this;
    }

    /** Вызывается из HandledScreenMixin для проверки — нужно ли подсветить слот. */
    public boolean shouldHighlight(ItemStack stack) {
        if (!isEnabled() || stack.isEmpty()) return false;
        return switch (filterMode.getSelected()) {
            case "netherite_armor" -> stack.isOf(Items.NETHERITE_HELMET)
                    || stack.isOf(Items.NETHERITE_CHESTPLATE)
                    || stack.isOf(Items.NETHERITE_LEGGINGS)
                    || stack.isOf(Items.NETHERITE_BOOTS);
            case "diamond_armor"   -> stack.isOf(Items.DIAMOND_HELMET)
                    || stack.isOf(Items.DIAMOND_CHESTPLATE)
                    || stack.isOf(Items.DIAMOND_LEGGINGS)
                    || stack.isOf(Items.DIAMOND_BOOTS);
            case "netherite_sword" -> stack.isOf(Items.NETHERITE_SWORD);
            case "diamond_sword"   -> stack.isOf(Items.DIAMOND_SWORD);
            case "totems"          -> stack.isOf(Items.TOTEM_OF_UNDYING);
            case "apples"          -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            case "potions"         -> stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION);
            default                -> false;
        };
    }

    public int getHighlightColor() { return highlightColor.getColor(); }
    public boolean isGroupByItem()  { return groupByItem.isValue(); }
    public boolean isHighlight3()   { return highlight3Slots.isValue(); }

    public static AuctionHelperModule getInstance() { return INSTANCE; }
}
