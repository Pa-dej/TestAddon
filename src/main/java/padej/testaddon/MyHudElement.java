package padej.testaddon;

import net.minecraft.client.gui.DrawContext;
import padej.soup.api.event.events.packet.PacketEvent;
import padej.soup.api.feature.draggable.AbstractDraggable;

public class MyHudElement extends AbstractDraggable {

    public MyHudElement() {
        super(
                "MyHud",   // имя — уникальный идентификатор
                10,        // начальный X
                10,        // начальный Y
                120,       // ширина
                40,        // высота
                true       // canDrag — можно ли перетаскивать
        );
    }

    // ── Рендер ────────────────────────────────────────────────────────────────

    @Override
    public void drawDraggable(DrawContext context) {
        float x = getRenderX();    // X с учётом масштаба
        float y = getRenderY();    // Y с учётом масштаба
        float w = getRenderWidth();
        float h = getRenderHeight();

        // рисуем фон, текст и т.д.
        // DrawEngineImpl.drawRoundedRect(context, x, y, w, h, 4, 0xAA000000);
        // fonts.inter12.drawString(context.getMatrices(), "Info", x + 4, y + 4, 0xFFFFFFFF);
    }

    // ── Видимость ─────────────────────────────────────────────────────────────

    @Override
    public boolean visible() {
        return mc.player != null && mc.world != null;
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // обновляем данные каждый тик
    }

    // ── Пакеты ───────────────────────────────────────────────────────────────

    @Override
    public void packet(PacketEvent e) {
        if (e.isSend()) return;
        // реагируем на входящие пакеты
    }
}
