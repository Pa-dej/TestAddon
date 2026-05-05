package padej.testaddon;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.event.types.Priority;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.ModuleCategory;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;

public class MyModule extends Module {

    // ── Настройки ──────────────────────────────────────────────────────────────

    private final SelectSetting mode = new SelectSetting("mode", "desc")
            .value("A", "B", "C")
            .selected("A");

    private final ValueSetting speed = new ValueSetting("speed", "desc")
            .range(0.5f, 5.0f)
            .setValue(1.0f)
            .visible(() -> !mode.isSelected("A"));   // видна только в режимах B и C

    private final BooleanSetting enabled = new BooleanSetting("extra", "desc")
            .setValue(false);

    private final ColorSetting color = new ColorSetting("color", "desc")
            .value(0xFFE91E63)
            .visible(enabled::isValue);              // видна только когда extra = true

    // ── Конструктор ────────────────────────────────────────────────────────────

    public MyModule() {
        super("module.my_module.name", ModuleCategory.OTHER);
        setup(mode, speed, enabled, color);
    }

    @EventHandler(Priority.HIGH)
    public void onDraw(DrawEvent e) {
        // рисование на HUD
        e.getDrawContext().fill(50, 50, 100, 100, color.getColor());
    }
}
