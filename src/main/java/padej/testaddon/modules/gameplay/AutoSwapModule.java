package padej.testaddon.modules.gameplay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;
import padej.testaddon.util.ServerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.AutoSwapModule}.
 *
 * <p><b>Назначение</b>: автоматическая подмена предмета в offhand. Два режима:
 * <ul>
 *     <li>{@code MENU} — открывает радиал {@link AutoSwapRadialScreen}, в нём
 *     до 8 saved-слотов (предметы привязываются цифрами 1..8 на main-hand);
 *     отпускание клавиши = выполнить swap наведённого слота.</li>
 *     <li>{@code DIRECT} — нажатие direct-клавиши: мгновенный swap по правилам
 *     sphere/talisman/totem (4 переключателя).</li>
 * </ul>
 *
 * <p><b>Механика swap'а</b> (1:1 с оригиналом, важно для FunTime):
 * <ol>
 *     <li>{@code mc.setScreen(new InventoryScreen(player))} — открыть инвентарь.</li>
 *     <li>Пауза {@code swapDelay} мс.</li>
 *     <li>{@code clickSlot(target, PICKUP)} — взять source.</li>
 *     <li>Пауза, {@code clickSlot(45, PICKUP)} — положить в offhand.</li>
 *     <li>Если offhand был не пуст: пауза, {@code clickSlot(target, PICKUP)} — вернуть.</li>
 *     <li>Пауза, {@code closeHandledScreen + setScreen(null)}.</li>
 * </ol>
 * Без открытия InventoryScreen клики по слотам инвентаря не доходят до сервера.</p>
 */
public class AutoSwapModule extends Module {

    public static final String SPHERE_DEFAULT   = "сфера";
    public static final String TALISMAN_DEFAULT = "талисман";

    public static final int MAX_SAVED_SLOTS = 8;

    private final SelectSetting mode = new SelectSetting(
            "auto_swap.mode.name", "auto_swap.mode.desc"
    ).value("menu", "direct").selected("menu");

    private final BindSetting menuKey = new BindSetting(
            "auto_swap.menu_key.name", "auto_swap.menu_key.desc"
    ).setKey(GLFW.GLFW_KEY_V);

    private final BindSetting directKey = new BindSetting(
            "auto_swap.direct_key.name", "auto_swap.direct_key.desc"
    ).setKey(GLFW.GLFW_KEY_B);

    private final ValueSetting swapDelay = new ValueSetting(
            "auto_swap.delay.name", "auto_swap.delay.desc"
    ).range(50, 500).setValue(100.0f).setInteger(true);

    private final ValueSetting maxSlots = new ValueSetting(
            "auto_swap.max_slots.name", "auto_swap.max_slots.desc"
    ).range(2, MAX_SAVED_SLOTS).setValue(3.0f).setInteger(true);

    private final ValueSetting hoverScale = new ValueSetting(
            "auto_swap.hover_scale.name", "auto_swap.hover_scale.desc"
    ).range(0, 16).setValue(5.0f);

    private final ValueSetting outlineThickness = new ValueSetting(
            "auto_swap.outline_thickness.name", "auto_swap.outline_thickness.desc"
    ).range(0, 6).setValue(2f);

    private final TextSetting sphereName = new TextSetting(
            "auto_swap.sphere.name", "auto_swap.sphere.desc"
    ).setText(SPHERE_DEFAULT).setMax(48);

    private final TextSetting talismanName = new TextSetting(
            "auto_swap.talisman.name", "auto_swap.talisman.desc"
    ).setText(TALISMAN_DEFAULT).setMax(48);

    private final BooleanSetting swapSphereToTotem    = new BooleanSetting(
            "auto_swap.sphere_to_totem.name", "auto_swap.sphere_to_totem.desc").setValue(true);
    private final BooleanSetting swapSphereToTalisman = new BooleanSetting(
            "auto_swap.sphere_to_talisman.name", "auto_swap.sphere_to_talisman.desc").setValue(false);
    private final BooleanSetting swapTalismanToTotem  = new BooleanSetting(
            "auto_swap.talisman_to_totem.name", "auto_swap.talisman_to_totem.desc").setValue(false);
    private final BooleanSetting swapSphereToSphere   = new BooleanSetting(
            "auto_swap.sphere_to_sphere.name", "auto_swap.sphere_to_sphere.desc").setValue(false);

    /** Сохранённые предметы радиала (имена). Конфиг хранит как TextSetting'и. */
    private final TextSetting[] savedSlots = new TextSetting[MAX_SAVED_SLOTS];

    private boolean isSwapping;
    private int directRotIndex;

    /** Нужно чтобы открыть радиал только один раз на одно нажатие menu-key (rising edge). */
    private boolean menuKeyHeldLastTick;
    private boolean directKeyHeldLastTick;

    public AutoSwapModule() {
        super("module.auto_swap.name", SoupBetterCategory.GAMEPLAY);
        List<padej.soup.api.feature.module.setting.Setting> all = new ArrayList<>();
        all.add(mode);
        all.add(menuKey);
        all.add(directKey);
        all.add(swapDelay);
        all.add(maxSlots);
        all.add(hoverScale);
        all.add(outlineThickness);
        all.add(sphereName);
        all.add(talismanName);
        all.add(swapSphereToTotem);
        all.add(swapSphereToTalisman);
        all.add(swapTalismanToTotem);
        all.add(swapSphereToSphere);
        for (int i = 0; i < savedSlots.length; i++) {
            // Saved-слоты — внутреннее хранилище для радиала, бинд/очистка
            // делается через ЛКМ/ПКМ внутри AutoSwapRadialScreen. Прячем
            // эти настройки от пользователя через .visible(() -> false).
            savedSlots[i] = new TextSetting(
                    "auto_swap.saved_" + (i + 1) + ".name",
                    "auto_swap.saved_" + (i + 1) + ".desc"
            ).setText("").setMax(48).visible(() -> false);
            all.add(savedSlots[i]);
        }
        setup(all.toArray(new padej.soup.api.feature.module.setting.Setting[0]));
    }

    // ─── KeyEvent: rising-edge детект для menu/direct клавиш ──────────────────

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.player == null) return;

        int mk = menuKey.getKey();
        int dk = directKey.getKey();

        boolean mkNow = mk != GLFW.GLFW_KEY_UNKNOWN && e.isKeyDown(mk);
        boolean dkNow = dk != GLFW.GLFW_KEY_UNKNOWN && e.isKeyDown(dk);

        // Direct: триггер по нажатию (rising edge). НЕ открывает радиал.
        if (dkNow && !directKeyHeldLastTick) {
            activateDirectSwap();
        }
        directKeyHeldLastTick = dkNow;

        // Menu: при rising edge — если режим menu → открыть радиал.
        // Если режим direct → крутить сохранённые слоты (как activateSavedItemSwap).
        if (mkNow && !menuKeyHeldLastTick) {
            if (mode.isSelected("direct")) {
                activateSavedItemSwap();
            } else if (mc.currentScreen == null) {
                mc.setScreen(new AutoSwapRadialScreen(this, mk));
            }
        }
        menuKeyHeldLastTick = mkNow;
    }

    // ─── Активаторы (вызываются из радиала и KeyEvent'а) ──────────────────────

    public void activateDirectSwap() {
        if (!ServerUtil.isFunTimeServer() || mc.player == null) return;
        SwapTarget target = findNextAvailableTargetWithRules();
        if (target != null) startSwapSequence(target);
    }

    public void activateSavedItemSwap() {
        if (!ServerUtil.isFunTimeServer() || mc.player == null) return;
        SwapTarget target = findNextSavedTarget();
        if (target != null) startSwapSequence(target);
    }

    /** Вызывается радиалом при выборе слота. */
    public void activateSavedSlotSwap(int savedIndex) {
        if (!ServerUtil.isFunTimeServer() || mc.player == null) return;
        if (savedIndex < 0 || savedIndex >= MAX_SAVED_SLOTS) return;
        String itemName = savedSlots[savedIndex].getText();
        if (itemName == null || itemName.isBlank()) return;
        int slotId = findItemSlotByName(itemName);
        if (slotId == -1) return;
        startSwapSequence(new SwapTarget(itemName, slotId));
    }

    // ─── API для радиала ──────────────────────────────────────────────────────

    public int getMaxSlotsValue() { return (int) maxSlots.getValue(); }

    public float getHoverScale() { return hoverScale.getValue(); }

    public float getOutlineThickness() { return outlineThickness.getValue(); }

    public String getSavedItem(int idx) {
        if (idx < 0 || idx >= MAX_SAVED_SLOTS) return null;
        String s = savedSlots[idx].getText();
        return (s == null || s.isBlank()) ? null : s;
    }

    public void setSavedSlot(int idx, String name) {
        if (idx < 0 || idx >= MAX_SAVED_SLOTS) return;
        savedSlots[idx].setText(name == null ? "" : name);
    }

    public void clearSavedSlot(int idx) {
        setSavedSlot(idx, "");
    }

    // ─── Swap mechanics (1:1 с оригиналом) ────────────────────────────────────

    private void startSwapSequence(SwapTarget target) {
        MinecraftClient client = mc;
        var player = client.player;
        if (player == null || client.interactionManager == null || isSwapping) return;
        if (target.slotId < 0 || target.slotId >= player.playerScreenHandler.slots.size()) return;

        isSwapping = true;
        // Открываем инвентарь СЕЙЧАС в потоке клиента — критически важно для
        // прохождения кликов слотов на сервер. Должно вызываться когда никакого
        // другого экрана уже нет (см. confirmAndClose в радиале — setScreen(null)
        // делается ДО вызова этого метода).
        client.execute(() -> client.setScreen(new InventoryScreen(player)));

        int delay = Math.max(50, (int) swapDelay.getValue());

        new Thread(() -> {
            try {
                Thread.sleep(delay);
                client.execute(() -> performSwap(target.slotId, delay));
            } catch (InterruptedException ignored) {
                client.execute(this::finishSwap);
            }
        }, "SoupBetter-AutoSwapStart").start();
    }

    private void performSwap(int targetSlot, int delay) {
        MinecraftClient client = mc;
        var player = client.player;
        if (player == null || client.interactionManager == null) {
            finishSwap();
            return;
        }
        int syncId = player.playerScreenHandler.syncId;
        int offhandSlot = 45;
        boolean hasOffhandItem = !player.getInventory().offHand.get(0).isEmpty();

        new Thread(() -> {
            try {
                client.execute(() -> client.interactionManager.clickSlot(
                        syncId, targetSlot, 0, SlotActionType.PICKUP, player));
                Thread.sleep(delay);
                client.execute(() -> client.interactionManager.clickSlot(
                        syncId, offhandSlot, 0, SlotActionType.PICKUP, player));

                if (hasOffhandItem) {
                    Thread.sleep(delay);
                    client.execute(() -> client.interactionManager.clickSlot(
                            syncId, targetSlot, 0, SlotActionType.PICKUP, player));
                }
                Thread.sleep(delay);
                client.execute(this::finishSwap);
            } catch (InterruptedException ignored) {
                client.execute(this::finishSwap);
            }
        }, "SoupBetter-AutoSwapSteps").start();
    }

    private void finishSwap() {
        MinecraftClient client = mc;
        if (client.player != null) client.player.closeHandledScreen();
        client.setScreen(null);
        isSwapping = false;
    }

    // ─── Target selection (правила Direct-режима) ─────────────────────────────

    private SwapTarget findNextAvailableTargetWithRules() {
        var player = mc.player;
        if (player == null) return null;
        ItemStack offhandStack = player.getInventory().offHand.get(0);
        String currentOffhandItem = offhandStack.isEmpty() ? null : offhandStack.getName().getString();

        boolean anyRule = swapSphereToTotem.isValue() || swapSphereToTalisman.isValue()
                || swapTalismanToTotem.isValue() || swapSphereToSphere.isValue();
        if (anyRule) return applySwapRules(currentOffhandItem);
        return null;
    }

    private SwapTarget applySwapRules(String currentItem) {
        String sphereKey   = sphereName.getText().toLowerCase(Locale.ROOT);
        String talismanKey = talismanName.getText().toLowerCase(Locale.ROOT);
        String lowerItem   = currentItem == null ? "" : currentItem.toLowerCase(Locale.ROOT);

        if (currentItem == null || currentItem.isEmpty()) {
            SwapTarget totem = findTotemTarget();
            if (totem != null) return totem;
            SwapTarget talisman = findItemContaining(talismanKey);
            if (talisman != null) return talisman;
            return findItemContaining(sphereKey);
        }

        if (isTotemName(currentItem)) {
            if (swapSphereToTotem.isValue() || swapSphereToTalisman.isValue()) {
                SwapTarget s = findItemContaining(sphereKey);
                if (s != null) return s;
            }
            if (swapTalismanToTotem.isValue()) {
                SwapTarget t = findItemContaining(talismanKey);
                if (t != null) return t;
            }
            return null;
        }

        if (lowerItem.contains(talismanKey)) {
            if (swapTalismanToTotem.isValue()) {
                SwapTarget totem = findTotemTarget();
                if (totem != null) return totem;
                if (swapSphereToTotem.isValue() || swapSphereToTalisman.isValue()) {
                    SwapTarget s = findItemContaining(sphereKey);
                    if (s != null) return s;
                }
            }
            if (swapSphereToTalisman.isValue()) {
                return findItemContaining(sphereKey);
            }
            return null;
        }

        if (lowerItem.contains(sphereKey)) {
            if (swapSphereToSphere.isValue()) {
                SwapTarget s = findItemContaining(sphereKey);
                if (s != null) return s;
            }
            if (swapSphereToTotem.isValue()) {
                SwapTarget totem = findTotemTarget();
                if (totem != null) return totem;
                SwapTarget talisman = findItemContaining(talismanKey);
                if (talisman != null) return talisman;
            } else if (swapSphereToTalisman.isValue()) {
                SwapTarget talisman = findItemContaining(talismanKey);
                if (talisman != null) return talisman;
                SwapTarget totem = findTotemTarget();
                if (totem != null) return totem;
            }
        }
        return null;
    }

    private SwapTarget findNextSavedTarget() {
        int n = getMaxSlotsValue();
        int attempts = 0;
        while (attempts < n) {
            String savedItem = getSavedItem(directRotIndex);
            directRotIndex = (directRotIndex + 1) % n;
            attempts++;
            if (savedItem == null) continue;
            int slotId = findItemSlotByName(savedItem);
            if (slotId != -1) return new SwapTarget(savedItem, slotId);
        }
        return null;
    }

    private SwapTarget findTotemTarget() {
        var player = mc.player;
        if (player == null) return null;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return new SwapTarget(stack.getName().getString(), i < 9 ? i + 36 : i);
            }
        }
        return null;
    }

    private SwapTarget findItemContaining(String searchTextLower) {
        var player = mc.player;
        if (player == null || searchTextLower == null || searchTextLower.isBlank()) return null;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty()) {
                String stackName = stack.getName().getString();
                if (stackName.toLowerCase(Locale.ROOT).contains(searchTextLower)) {
                    return new SwapTarget(stackName, i < 9 ? i + 36 : i);
                }
            }
        }
        return null;
    }

    private boolean isTotemName(String itemName) {
        return itemName != null
                && (itemName.equalsIgnoreCase("Totem of Undying")
                || itemName.toLowerCase(Locale.ROOT).contains("тотем"));
    }

    private int findItemSlotByName(String itemName) {
        var player = mc.player;
        if (player == null) return -1;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty() && stack.getName().getString().equals(itemName)) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    private record SwapTarget(String itemName, int slotId) {}
}
