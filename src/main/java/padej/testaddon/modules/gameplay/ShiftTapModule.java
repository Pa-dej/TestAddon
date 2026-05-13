package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
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
        if (mc.player == null || !e.isKeyDown(key.getKey())) return;
        // Симулируем короткое нажатие сника
        tapEndMs = System.currentTimeMillis() + (long) tapDurationMs.getValue();
        mc.options.sneakKey.setPressed(true);
    }

    @Override
    public void activate() { tapEndMs = 0; }

    @Override
    public void deactivate() {
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
    }

    // Вызывается из TickEvent или мixin для сброса нажатия
    public void tick() {
        if (tapEndMs > 0 && System.currentTimeMillis() >= tapEndMs) {
            tapEndMs = 0;
            if (mc.options != null) mc.options.sneakKey.setPressed(false);
        }
    }
}
