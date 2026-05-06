package padej.testaddon;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.event.types.Priority;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;

/**
 * Демо-модуль: рисует прямоугольник на экране.
 * Находится в кастомной категории {@link TestAddonCategory#MAIN}.
 */
public class MyModule extends Module {

    // ── Настройки ──────────────────────────────────────────────────────────────

    private final SelectSetting mode = new SelectSetting("mode", "desc")
            .value("A", "B", "C")
            .selected("A");

    private final ValueSetting speed = new ValueSetting("speed", "desc")
            .range(0.5f, 5.0f)
            .setValue(1.0f)
            .visible(() -> !mode.isSelected("A"));   // видна только в режимах B и C

    private final BooleanSetting extra = new BooleanSetting("extra", "desc")
            .setValue(false);

    private final ColorSetting color = new ColorSetting("color", "desc")
            .value(0xFFE91E63)
            .visible(extra::isValue);                // видна только когда extra = true

    // ── Конструктор ────────────────────────────────────────────────────────────

    public MyModule() {
        super("module.my_module.name", TestAddonCategory.MAIN);
        setup(mode, speed, extra, color);
    }

    // ── Обработчики событий ────────────────────────────────────────────────────

    @EventHandler(Priority.HIGH)
    public void onDraw(DrawEvent e) {
        int fillColor = extra.isValue() ? color.getColor() : 0xAAE91E63;

        // Размер прямоугольника зависит от режима
        int size = mode.isSelected("A") ? 50 : mode.isSelected("B") ? 80 : 120;
        e.getDrawContext().fill(10, 10, 10 + size, 10 + size, fillColor);
    }
}
