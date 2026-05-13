package padej.testaddon.modules.server;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.AuctionHelperModule}.
 *
 * <p><b>Назначение</b>: на экране аукциона FunTime подсвечивает три слота с
 * самыми низкими ценами (опционально по выбранному фильтру предметов).
 * Цена извлекается из lore/NBT/имени предмета (три fallback'а).</p>
 *
 * <p><b>Режимы</b>:
 * <ul>
 *     <li>{@code groupByItem=false} (по умолчанию): три самых дешёвых лота
 *     среди всех подходящих под фильтр предметов.</li>
 *     <li>{@code groupByItem=true}: сначала находим группу с самой дешёвой
 *     ценой за штуку (тип предмета с самым низким минимумом),
 *     потом берём в ней 3 самых дешёвых.</li>
 * </ul></p>
 *
 * <p>Подсветка применяется по {@link Slot#id}, не по типу предмета — рендер
 * вызывается из {@code ItemHighlightMixin#onDrawSlot} через
 * {@link #shouldHighlight(Slot)}.</p>
 */
public class AuctionHelperModule extends Module {

    public enum FilterMode {
        ALL, NETHERITE_ARMOR, DIAMOND_ARMOR, NETHERITE_SWORD, DIAMOND_SWORD,
        TOTEMS, APPLES, POTIONS
    }

    private static AuctionHelperModule INSTANCE;

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

    /** Идентификаторы слотов, которые сейчас нужно подсветить. */
    private final Set<Integer> cheapestSlotIds = new HashSet<>();

    public AuctionHelperModule() {
        super("module.auction_helper.name", SoupBetterCategory.SERVER);
        setup(filterMode, groupByItem, highlight3Slots, highlightColor);
        INSTANCE = this;
    }

    public static AuctionHelperModule getInstance() { return INSTANCE; }

    /** Вызывается из ItemHighlightMixin. */
    public boolean shouldHighlight(Slot slot) {
        if (!isEnabled() || slot == null) return false;
        return cheapestSlotIds.contains(slot.id);
    }

    public int getHighlightColor() { return highlightColor.getColor(); }

    @EventHandler
    public void onTick(TickEvent e) {
        cheapestSlotIds.clear();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        String title = screen.getTitle().getString();
        if (!title.contains("Аукцион") && !title.contains("Поиск:")) return;

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        boolean three = highlight3Slots.isValue();
        FilterMode mode = currentFilterMode();

        if (groupByItem.isValue()) {
            // Группируем по типу предмета, ищем группу с самым дешёвым per-item.
            Map<Item, List<SlotPrice>> itemGroups = new HashMap<>();
            for (Slot slot : handler.slots) {
                if (slot.id > 44) continue;
                ItemStack stack = slot.getStack();
                if (stack.isEmpty() || !matchesFilter(stack, mode)) continue;
                int totalPrice = extractPriceFromStack(stack);
                int count = stack.getCount();
                if (totalPrice == -1 || count == 0) continue;
                double pricePerItem = (double) totalPrice / count;
                itemGroups.computeIfAbsent(stack.getItem(), k -> new ArrayList<>())
                        .add(new SlotPrice(slot, pricePerItem));
            }
            double cheapestPrice = Double.MAX_VALUE;
            List<SlotPrice> cheapestGroup = null;
            for (List<SlotPrice> group : itemGroups.values()) {
                if (group.isEmpty()) continue;
                double minPrice = group.stream().mapToDouble(sp -> sp.pricePerItem).min().orElse(Double.MAX_VALUE);
                if (minPrice < cheapestPrice) {
                    cheapestPrice = minPrice;
                    cheapestGroup = group;
                }
            }
            if (cheapestGroup != null) {
                cheapestGroup.sort(Comparator.comparingDouble(sp -> sp.pricePerItem));
                if (!cheapestGroup.isEmpty()) cheapestSlotIds.add(cheapestGroup.get(0).slot.id);
                if (three && cheapestGroup.size() > 1) cheapestSlotIds.add(cheapestGroup.get(1).slot.id);
                if (three && cheapestGroup.size() > 2) cheapestSlotIds.add(cheapestGroup.get(2).slot.id);
            }
        } else {
            // Топ-3 самых дешёвых среди всех подходящих лотов.
            double fsPrice = Double.MAX_VALUE, medPrice = Double.MAX_VALUE, thPrice = Double.MAX_VALUE;
            Slot slot1 = null, slot2 = null, slot3 = null;
            for (Slot slot : handler.slots) {
                if (slot.id > 44) continue;
                ItemStack stack = slot.getStack();
                if (stack.isEmpty() || !matchesFilter(stack, mode)) continue;
                int totalPrice = extractPriceFromStack(stack);
                int count = stack.getCount();
                if (totalPrice == -1 || count == 0) continue;
                double pricePerItem = (double) totalPrice / count;

                if (pricePerItem < fsPrice) {
                    thPrice = medPrice; slot3 = slot2;
                    medPrice = fsPrice; slot2 = slot1;
                    fsPrice = pricePerItem; slot1 = slot;
                } else if (three && pricePerItem < medPrice) {
                    thPrice = medPrice; slot3 = slot2;
                    medPrice = pricePerItem; slot2 = slot;
                } else if (three && pricePerItem < thPrice) {
                    thPrice = pricePerItem; slot3 = slot;
                }
            }
            if (slot1 != null) cheapestSlotIds.add(slot1.id);
            if (slot2 != null) cheapestSlotIds.add(slot2.id);
            if (slot3 != null) cheapestSlotIds.add(slot3.id);
        }
    }

    private FilterMode currentFilterMode() {
        return switch (filterMode.getSelected()) {
            case "netherite_armor" -> FilterMode.NETHERITE_ARMOR;
            case "diamond_armor"   -> FilterMode.DIAMOND_ARMOR;
            case "netherite_sword" -> FilterMode.NETHERITE_SWORD;
            case "diamond_sword"   -> FilterMode.DIAMOND_SWORD;
            case "totems"          -> FilterMode.TOTEMS;
            case "apples"          -> FilterMode.APPLES;
            case "potions"         -> FilterMode.POTIONS;
            default                -> FilterMode.ALL;
        };
    }

    private boolean matchesFilter(ItemStack stack, FilterMode mode) {
        Item item = stack.getItem();
        return switch (mode) {
            case ALL -> true;
            case NETHERITE_ARMOR -> item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
                    || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS;
            case DIAMOND_ARMOR -> item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS;
            case NETHERITE_SWORD -> item == Items.NETHERITE_SWORD;
            case DIAMOND_SWORD   -> item == Items.DIAMOND_SWORD;
            case TOTEMS          -> item == Items.TOTEM_OF_UNDYING;
            case APPLES          -> item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.APPLE;
            case POTIONS         -> item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
        };
    }

    /**
     * Извлекает цену из стака. Три fallback'а 1:1 с оригиналом:
     * 1) CUSTOM_DATA NBT с устаревшим JSON-кодированным lore (на FunTime местами всё ещё встречается);
     * 2) современный LORE-компонент (line.contains("цена") → парсим цифры);
     * 3) имя предмета.
     */
    private int extractPriceFromStack(ItemStack stack) {
        // 2) Современный lore-компонент.
        var loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent != null) {
            for (var line : loreComponent.lines()) {
                String text = line.getString().toLowerCase(Locale.ROOT);
                if (text.contains("цена") || text.contains("ценa")) {
                    String priceStr = text.replaceAll("[^0-9]", "");
                    if (!priceStr.isEmpty()) {
                        try { return Integer.parseInt(priceStr); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        // 3) Имя.
        String itemName = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (itemName.contains("цена") || itemName.contains("ценa")) {
            String priceStr = itemName.replaceAll("[^0-9]", "");
            if (!priceStr.isEmpty()) {
                try { return Integer.parseInt(priceStr); } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    private static class SlotPrice {
        final Slot slot;
        final double pricePerItem;
        SlotPrice(Slot slot, double pricePerItem) {
            this.slot = slot;
            this.pricePerItem = pricePerItem;
        }
    }
}
