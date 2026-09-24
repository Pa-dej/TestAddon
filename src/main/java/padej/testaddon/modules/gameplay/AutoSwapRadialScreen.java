package padej.testaddon.modules.gameplay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import padej.testaddon.render2d.RenderRadialMenu;

import java.util.ArrayList;
import java.util.List;

import static padej.soup.core.Main.mc;

/**
 * Радиальное меню AutoSwap — тонкая обёртка над {@link Screen}.
 *
 * <p>Сам по себе ничего не рисует: всю отрисовку, шейдеры и анимацию
 * делегирует в {@link RenderRadialMenu}. Здесь живут только переопределённые
 * методы Screen'а (input, lifecycle) и доменное состояние (slots, hovered,
 * holdKey, ссылка на модуль).</p>
 *
 * <p><b>Управление</b>: ЛКМ по пустому слоту — bind main-hand; ЛКМ по
 * заполненному — swap; ПКМ — очистить; отпускание menu-key — действие на
 * наведённом секторе; ESC — закрыть.</p>
 *
 * <p><b>Hover-зона</b>: только внутреннее мёртвое пятно (см.
 * {@link RenderRadialMenu#getHoverIndex}) — внешнего бордюра нет, можно
 * быстрым движением выбрать сектор далеко за пределами кольца.</p>
 */
public class AutoSwapRadialScreen extends Screen {

    private final AutoSwapModule module;
    private final int holdKey;
    private final long openedAt;

    private final List<SwapSlotView> slots = new ArrayList<>();
    private final int maxSlots;
    private final RenderRadialMenu renderer;

    private int hovered = -1;
    /** Индекс слота, из которого игрок перетаскивает предмет (LMB hold).
     *  -1 = drag не активен. */
    private int draggingFromIdx = -1;
    /** Последняя зафиксированная позиция мыши — используется для отрисовки
     *  drag-иконки в render(). DrawContext в mouseClicked/mouseReleased
     *  недоступен, поэтому позицию запоминаем для render-фазы. */
    private int lastMouseX, lastMouseY;

    public AutoSwapRadialScreen(AutoSwapModule module, int holdKey) {
        super(Text.literal("AutoSwap"));
        this.module = module;
        this.holdKey = holdKey;
        this.openedAt = System.currentTimeMillis();
        this.maxSlots = Math.max(2, Math.min(8, module.getMaxSlotsValue()));
        this.renderer = new RenderRadialMenu(maxSlots);
        rebuildSlots();
    }

    // ── Slot helpers ──────────────────────────────────────────────────────────

    private void rebuildSlots() {
        slots.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        for (int i = 0; i < maxSlots; i++) {
            String name = module.getSavedItem(i);
            ItemStack live = findItemByName(client, name);
            // Если предмета сейчас нет в инвентаре — берём из NBT-кэша
            // SoupAPI/files/soup_better/autoswap.nbt, чтобы иконка всё равно
            // отображалась (визуальный bind остался даже после смерти/складирования).
            // Флаг `available` различает живой стек и fallback — рендер
            // рисует fallback в grayscale.
            boolean available = live != null;
            ItemStack icon = available ? live : module.getSavedStack(i);
            slots.add(new SwapSlotView(icon, name, i, available));
        }
    }

    private ItemStack findItemByName(MinecraftClient client, String name) {
        if (name == null || name.isBlank() || client.player == null) return null;
        var inv = client.player.getInventory();
        for (int j = 0; j < inv.main.size(); j++) {
            ItemStack s = inv.main.get(j);
            if (!s.isEmpty() && s.getName().getString().equals(name)) return s.copy();
        }
        ItemStack off = inv.offHand.getFirst();
        if (!off.isEmpty() && off.getName().getString().equals(name)) return off.copy();
        return null;
    }

    // ── Screen overrides ──────────────────────────────────────────────────────

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void init() {
        super.init();
        // Когда любой Screen открывается, ванилла сбрасывает pressed у биндов,
        // привязанных к зажатым ДО открытия клавишам. Наш forwardMovementKey
        // отработает только на СЛЕДУЮЩЕЕ нажатие. Чтобы движение не прервалось,
        // одноразово опрашиваем GLFW по фактически привязанной клавише
        // КАЖДОГО movement-бинда (берём её из options, поэтому работает с любой
        // пользовательской раскладкой — RDFG, IJKL, стрелки, что угодно).
        long window = mc.getWindow().getHandle();
        var o = mc.options;
        KeyBinding[] binds = {
                o.forwardKey, o.backKey, o.leftKey, o.rightKey,
                o.jumpKey, o.sneakKey, o.sprintKey
        };
        for (KeyBinding kb : binds) {
            InputUtil.Key boundKey = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
            // Опрашиваем только клавиатурные бинды; MOUSE / SCANCODE через
            // glfwGetKey не достаются, для них init-sync пропускаем.
            if (boundKey.getCategory() != InputUtil.Type.KEYSYM) continue;
            int code = boundKey.getCode();
            if (code == GLFW.GLFW_KEY_UNKNOWN) continue;
            if (GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS) {
                kb.setPressed(true);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Не вызываем super.render — не нужен ванильный фон Screen.

        // close-анимация отыграла → просто закрываем экран. Свап (если был)
        // уже запущен в confirmAndClose/mouseClicked — анимация чисто визуальная.
        if (renderer.closeFinished()) {
            mc.setScreen(null);
            return;
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Во время close-анимации hover заморожен — выбор уже зафиксирован.
        hovered = renderer.isClosing()
                ? -1
                : renderer.getHoverIndex(mouseX, mouseY, this.width, this.height);

        int bgColor      = padej.soup.base.util.color.ColorUtil.getRect(0.7f);
        int outlineColor = outlineClientColor();

        renderer.render(
                ctx, this.width, this.height,
                slots, hovered,
                module.getHoverScale(),
                module.getOutlineThickness(),
                bgColor, outlineColor,
                this.textRenderer,
                getHintText()
        );

        // Drag-overlay: иконка перетаскиваемого слота едет за курсором.
        // Renderer для этого слота пропускает отрисовку в секторе (см.
        // SlotView.isDragging), здесь мы рисуем её ровно под курсором.
        if (draggingFromIdx >= 0 && draggingFromIdx < slots.size()) {
            SwapSlotView src = slots.get(draggingFromIdx);
            ItemStack ds = src.itemStack;
            if (ds != null && !ds.isEmpty()) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(lastMouseX, lastMouseY, 0);
                ctx.getMatrices().scale(1.5f, 1.5f, 1f);
                ctx.drawItem(ds, -8, -8);
                ctx.getMatrices().pop();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renderer.isClosing()) return false;
        if (hovered < 0) return false;
        SwapSlotView slot = slots.get(hovered);

        if (button == 0) {
            if (slot.savedItemName == null || slot.savedItemName.isBlank()) {
                // Пустой слот: bind предмета из руки — без закрытия меню.
                if (mc.player != null) {
                    ItemStack held = mc.player.getMainHandStack();
                    if (!held.isEmpty()) {
                        String name = held.getName().getString();
                        slot.itemStack = held.copy();
                        slot.savedItemName = name;
                        slot.available = true;   // только что взят из руки
                        // Стек сохраняется в NBT-файл — иконка переживёт
                        // отсутствие предмета в инвентаре (склад / смерть).
                        module.setSavedSlot(hovered, name, held);
                    }
                }
            } else {
                // Заполненный слот: начинаем DRAG. Активация swap'а делается
                // отпусканием menu-key (confirmAndClose), а не ЛКМ.
                draggingFromIdx = hovered;
                slot.dragging = true;
            }
            return true;
        }
        if (button == 1) {
            // ПКМ — удаление предмета из слота. Если параллельно идёт drag
            // и удаляется источник — отменяем drag.
            slot.itemStack = null;
            slot.savedItemName = null;
            slot.dragging = false;
            slot.available = false;
            module.clearSavedSlot(hovered);
            if (draggingFromIdx == hovered) draggingFromIdx = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingFromIdx >= 0) {
            int from = draggingFromIdx;
            draggingFromIdx = -1;
            if (from < slots.size()) slots.get(from).dragging = false;

            // Drop: если курсор на другом слоте — меняем содержимое
            // местами (swap). Если на том же или вне зоны — drag отменяется.
            if (hovered >= 0 && hovered != from) {
                module.swapSavedSlots(from, hovered);
                rebuildSlots();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Пробрасываем нажатие в movement-бинды — не «съедаем» событие, чтобы
        // ESC и прочая логика Screen работала штатно.
        forwardMovementKey(keyCode, scanCode, true);

        mc.options.forwardKey.setPressed(mc.options.forwardKey.isPressed());

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // ESC — закрытие без активации, просто запускаем визуальную close.
            renderer.startClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        forwardMovementKey(keyCode, scanCode, false);
        if (keyCode == holdKey) {
            confirmAndClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (renderer.isClosing()) return;
        if (System.currentTimeMillis() - openedAt < 80) return;
        long window = mc.getWindow().getHandle();
        if (GLFW.glfwGetKey(window, holdKey) == GLFW.GLFW_RELEASE) {
            confirmAndClose();
        }
        for (SwapSlotView slot : slots) {
            if (slot.savedItemName != null) {
                ItemStack live = findItemByName(mc, slot.savedItemName);
                slot.available = live != null;
                slot.itemStack = slot.available ? live : module.getSavedStack(slot.index);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Запускает закрытие. Если под курсором заполненный слот — стартуем swap
     *  немедленно (не ждём окончания close-анимации), сама анимация играется
     *  чисто визуально параллельно. */
    private void confirmAndClose() {
        int sel = hovered;
        if (sel >= 0 && sel < slots.size()) {
            SwapSlotView slot = slots.get(sel);
            if (slot.savedItemName != null && !slot.savedItemName.isBlank()) {
                module.activateSavedSlotSwap(sel);
            }
        }
        renderer.startClose();
    }

    /** Пробрасывает движение игрока сквозь Screen: если код клавиши совпадает
     *  с одним из movement-биндов — зовём {@code kb.setPressed}.
     *  {@code matchesKey} умеет сравнивать привязанную клавишу с GLFW-кодом;
     *  {@code KeyBinding.equals(...)} здесь не годится — это Object.equals
     *  (reference equality), всегда вернёт false для InputUtil.Key. */
    private void forwardMovementKey(int keyCode, int scanCode, boolean pressed) {
        var o = MinecraftClient.getInstance().options;
        KeyBinding[] binds = {
                o.forwardKey, o.backKey, o.leftKey, o.rightKey,
                o.jumpKey, o.sneakKey, o.sprintKey
        };
        for (KeyBinding kb : binds) {
            if (kb.matchesKey(keyCode, scanCode)) {
                kb.setPressed(pressed);
                return;
            }
        }
    }

    private @NotNull String getHintText() {
        if (hovered >= 0) {
            String name = slots.get(hovered).savedItemName;
            if (name != null && !name.isBlank()) {
                return "ЛКМ или отпусти клавишу: свап на " + name + ". ПКМ — очистить.";
            }
            return "Слот " + (hovered + 1) + " пуст — ЛКМ привяжет предмет из руки.";
        }
        return "Наведи мышь на сектор. ЛКМ — bind/swap, ПКМ — очистить.";
    }

    /** Цвет обводки — акцент клиента (clientColor через ColorUtil).
     *  Принудительный alpha=0xFF на случай низкой настройки темы. */
    private int outlineClientColor() {
        int c = padej.soup.base.util.color.ColorUtil.getClientColor();
        return (c & 0x00FFFFFF) | 0xFF000000;
    }

    // ── Domain data ───────────────────────────────────────────────────────────

    /** Реализация {@link RenderRadialMenu.SlotView} для рендера + хранение
     *  изначального названия сохранённого предмета. */
    private static class SwapSlotView implements RenderRadialMenu.SlotView {
        ItemStack itemStack;
        String savedItemName;
        final int index;
        /** True если itemStack — живой стек из инвентаря, false если fallback
         *  из NBT-кэша. Управляет grayscale-рендером в {@link RenderRadialMenu}. */
        boolean available;
        /** True если игрок прямо сейчас тащит иконку этого слота — рендер
         *  пропускает её, Screen рисует поверх курсора. */
        boolean dragging;

        SwapSlotView(ItemStack itemStack, String savedItemName, int index, boolean available) {
            this.itemStack = itemStack;
            this.savedItemName = savedItemName;
            this.index = index;
            this.available = available;
        }

        @Override public ItemStack itemStack()     { return itemStack; }
        @Override public String    savedItemName() { return savedItemName; }
        @Override public boolean   isAvailable()   { return available; }
        @Override public boolean   isDragging()    { return dragging; }
    }
}
