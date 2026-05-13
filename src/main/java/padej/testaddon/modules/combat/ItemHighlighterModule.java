package padej.testaddon.modules.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.MultiSelectSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.soup.base.util.color.ColorUtil;
import padej.testaddon.SoupBetterCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.ItemHighlighterModule}.
 *
 * <p>Подсвечивает слоты инвентаря с заранее заданными «серверными» предметами
 * (12 встроенных пресетов с кириллическими именами) + до трёх пользовательских
 * пресетов с собственным запросом и цветом.</p>
 *
 * <p><b>Логика матчинга 1:1 с оригиналом</b>:
 * <ul>
 *     <li>Если у пресета задан {@code customName} — требуется совпадение <i>Item</i>
 *     И contains() по локализованному имени (toLowerCase).</li>
 *     <li>Если {@code customName == null} — только совпадение <i>Item</i>.</li>
 *     <li>Пользовательский пресет матчится contains() по локализованному имени
 *     ИЛИ по registry-id предмета.</li>
 * </ul></p>
 *
 * <p>Вызывается из {@code ItemHighlightMixin#onDrawSlot} через
 * {@link #getSlotHighlightColor(ItemStack)}.</p>
 */
public class ItemHighlighterModule extends Module {

    private static ItemHighlighterModule INSTANCE;

    /**
     * 12 встроенных пресетов под FunTime/AresMine-сервера.
     * Имена и rgb-значения скопированы из оригинала 1:1.
     */
    public enum HighlightItem {
        DEZORIENTACIYA      ("Дезориентация",       Items.ENDER_EYE,             "дезориентация", 0.0f, 1.0f, 0.0f),
        OGNENNY_SMERCH      ("Огненный смерч",      Items.FIRE_CHARGE,           "огненный смерч", 1.0f, 0.55f, 0.0f),
        YAVNAYA_PYL         ("Явная пыль",          Items.SUGAR,                 "явная пыль",     1.0f, 1.0f, 1.0f),
        TOTEM               ("Тотем бессмертия",    Items.TOTEM_OF_UNDYING,      null,             1.0f, 0.8f, 0.0f),
        EXPERIENCE_BOTTLE   ("Бутылочка опыта",     Items.EXPERIENCE_BOTTLE,     null,             0.4f, 1.0f, 0.8f),
        TRAPKA              ("Трапка",              Items.NETHERITE_SCRAP,       "трапка",         0.76f, 0.7f, 0.55f),
        PLAST               ("Пласт",               Items.DRIED_KELP,            "пласт",          0.5f, 0.5f, 0.5f),
        GOLDEN_APPLE        ("Золотое яблоко",      Items.GOLDEN_APPLE,          null,             1.0f, 0.84f, 0.0f),
        ENCHANTED_GOLDEN_APPLE("Чар гепл",          Items.ENCHANTED_GOLDEN_APPLE, null,            1.0f, 0.4f, 0.8f),
        CHORUS_FRUIT        ("Хорус",               Items.CHORUS_FRUIT,          null,             0.58f, 0.0f, 0.83f),
        ENDER_PEARL         ("Эндер перл",          Items.ENDER_PEARL,           null,             0.4f, 0.8f, 0.6f),
        SNOWBALL            ("Снежок",              Items.SNOWBALL,              null,             0.68f, 0.85f, 0.9f);

        private final String displayName;
        private final Item item;
        private final String customName;
        private final float r, g, b;

        HighlightItem(String displayName, Item item, String customName, float r, float g, float b) {
            this.displayName = displayName;
            this.item = item;
            this.customName = customName;
            this.r = r; this.g = g; this.b = b;
        }

        public String getDisplayName() { return displayName; }
        public Item getItem()          { return item; }
        public String getCustomName()  { return customName; }
        public boolean isCustomItem()  { return customName != null; }
        public float getR()            { return r; }
        public float getG()            { return g; }
        public float getB()            { return b; }

        /** Цвет slot-оверлея — RGB + альфа 0.4 (как в оригинале). */
        public int color(float alpha) {
            return ColorUtil.getColor((int)(r*255), (int)(g*255), (int)(b*255), (int)(alpha*255));
        }
    }

    /** Множественный выбор: какие встроенные пресеты активны. */
    private final MultiSelectSetting enabledPresets;

    private final ValueSettingAlpha alpha = new ValueSettingAlpha();
    /** Кастомные пресеты — три слота, каждый имеет query + цвет + on/off. */
    private final BooleanSetting custom1Enabled = new BooleanSetting(
            "item_hl.custom1.enabled.name", "item_hl.custom1.enabled.desc").setValue(false);
    private final TextSetting custom1Query = new TextSetting(
            "item_hl.custom1.query.name", "item_hl.custom1.query.desc")
            .setText("").setMax(48).visible(custom1Enabled::isValue);
    private final ColorSetting custom1Color = new ColorSetting(
            "item_hl.custom1.color.name", "item_hl.custom1.color.desc")
            .value(0xFFC85766).visible(custom1Enabled::isValue);

    private final BooleanSetting custom2Enabled = new BooleanSetting(
            "item_hl.custom2.enabled.name", "item_hl.custom2.enabled.desc").setValue(false);
    private final TextSetting custom2Query = new TextSetting(
            "item_hl.custom2.query.name", "item_hl.custom2.query.desc")
            .setText("").setMax(48).visible(custom2Enabled::isValue);
    private final ColorSetting custom2Color = new ColorSetting(
            "item_hl.custom2.color.name", "item_hl.custom2.color.desc")
            .value(0xFFC85766).visible(custom2Enabled::isValue);

    private final BooleanSetting custom3Enabled = new BooleanSetting(
            "item_hl.custom3.enabled.name", "item_hl.custom3.enabled.desc").setValue(false);
    private final TextSetting custom3Query = new TextSetting(
            "item_hl.custom3.query.name", "item_hl.custom3.query.desc")
            .setText("").setMax(48).visible(custom3Enabled::isValue);
    private final ColorSetting custom3Color = new ColorSetting(
            "item_hl.custom3.color.name", "item_hl.custom3.color.desc")
            .value(0xFFC85766).visible(custom3Enabled::isValue);

    private final Map<HighlightItem, Boolean> cache = new EnumMap<>(HighlightItem.class);

    public ItemHighlighterModule() {
        super("module.item_highlighter.name", SoupBetterCategory.COMBAT);

        // Все 12 названий по умолчанию включены.
        String[] all = new String[HighlightItem.values().length];
        for (int i = 0; i < all.length; i++) all[i] = HighlightItem.values()[i].name();
        enabledPresets = new MultiSelectSetting(
                "item_hl.presets.name", "item_hl.presets.desc"
        ).value(all).selected(all);

        setup(
                enabledPresets, alpha.setting,
                custom1Enabled, custom1Query, custom1Color,
                custom2Enabled, custom2Query, custom2Color,
                custom3Enabled, custom3Query, custom3Color
        );
        INSTANCE = this;
    }

    public static ItemHighlighterModule getInstance() { return INSTANCE; }

    /** Используется из ItemHighlightMixin. Возвращает 0 если предмет не должен подсвечиваться. */
    public int getSlotHighlightColor(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        String itemName = stack.getName().getString().toLowerCase(Locale.ROOT);
        String registryName = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);

        // 1. Сначала пользовательские пресеты.
        int customColor = tryCustom(custom1Enabled, custom1Query, custom1Color, itemName, registryName);
        if (customColor != 0) return customColor;
        customColor = tryCustom(custom2Enabled, custom2Query, custom2Color, itemName, registryName);
        if (customColor != 0) return customColor;
        customColor = tryCustom(custom3Enabled, custom3Query, custom3Color, itemName, registryName);
        if (customColor != 0) return customColor;

        // 2. Встроенные пресеты.
        for (HighlightItem h : HighlightItem.values()) {
            if (!enabledPresets.isSelected(h.name())) continue;

            boolean matches;
            if (h.isCustomItem()) {
                matches = stack.getItem() == h.getItem()
                        && itemName.contains(h.getCustomName().toLowerCase(Locale.ROOT));
            } else {
                matches = stack.getItem() == h.getItem();
            }
            if (matches) return h.color(alpha.get());
        }
        return 0;
    }

    private int tryCustom(BooleanSetting enabled, TextSetting query, ColorSetting color,
                          String itemName, String registryName) {
        if (!enabled.isValue()) return 0;
        String q = query.getText();
        if (q == null || q.isBlank()) return 0;
        String qLow = q.toLowerCase(Locale.ROOT);
        if (itemName.contains(qLow) || registryName.contains(qLow)) {
            return ColorUtil.replAlpha(color.getColorWithAlpha(),
                    Math.max(25, ColorUtil.alpha(color.getColorWithAlpha())));
        }
        return 0;
    }

    /** Прокся для ValueSetting чтобы держать чтение alpha в одном месте. */
    private static class ValueSettingAlpha {
        final padej.soup.api.feature.module.setting.implement.ValueSetting setting =
                new padej.soup.api.feature.module.setting.implement.ValueSetting(
                        "item_hl.alpha.name", "item_hl.alpha.desc")
                        .range(0.1f, 1.0f).setValue(0.4f);
        float get() { return setting.getValue(); }
    }
}
