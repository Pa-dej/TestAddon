package padej.testaddon.modules.gameplay;

import org.lwjgl.glfw.GLFW;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

public class ShiftTapModule extends Module {

    private final BindSetting key = new BindSetting(
            "shift_tap.key.name", "shift_tap.key.desc"
    );

    private final ValueSetting tapDurationMs = new ValueSetting(
            "shift_tap.duration.name", "shift_tap.duration.desc"
    ).range(20, 200).setValue(50.0f);

    private long tapEndMs = 0;

    public ShiftTapModule() {
        super("module.shift_tap.name", SoupBetterCategory.GAMEPLAY);
        setup(key, tapDurationMs);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        int k = key.getKey();
        if (k == GLFW.GLFW_KEY_UNKNOWN || mc.player == null) return;
        if (!e.isKeyDown(k)) return;

        // Симулируем короткое нажатие сника: запускаем таймер и зажимаем клавишу.
        tapEndMs = System.currentTimeMillis() + (long) tapDurationMs.getValue();
        mc.options.sneakKey.setPressed(true);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        // Снимаем зажатие, когда время вышло. Без @EventHandler этот код раньше
        // не вызывался и сник "залипал" до выключения модуля.
        if (tapEndMs > 0 && System.currentTimeMillis() >= tapEndMs) {
            tapEndMs = 0;
            if (mc.options != null) mc.options.sneakKey.setPressed(false);
        }
    }

    @Override
    public void activate() {
        tapEndMs = 0;
    }

    @Override
    public void deactivate() {
        tapEndMs = 0;
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
    }
}
