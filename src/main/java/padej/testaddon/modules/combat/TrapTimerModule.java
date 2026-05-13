package padej.testaddon.modules.combat;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;

import padej.testaddon.SoupBetterCategory;

public class TrapTimerModule extends Module {

    public enum TrapType {
        NORMAL("Обычная", 15_000),
        DRAGON("Драконья", 20_000);

        public final String displayName;
        public final int durationMs;
        TrapType(String n, int d) { displayName = n; durationMs = d; }
    }

    private final SelectSetting trapType = new SelectSetting(
            "trap_timer.type.name", "trap_timer.type.desc"
    ).value("normal", "dragon").selected("normal");

    private final BindSetting startKey = new BindSetting(
            "trap_timer.start.name", "trap_timer.start.desc"
    );

    private final BindSetting resetKey = new BindSetting(
            "trap_timer.reset.name", "trap_timer.reset.desc"
    );

    private final ColorSetting activeColor = new ColorSetting(
            "trap_timer.active_color.name", "trap_timer.active_color.desc"
    ).value(0xFF4CAF50);

    private final ColorSetting expiredColor = new ColorSetting(
            "trap_timer.expired_color.name", "trap_timer.expired_color.desc"
    ).value(0xFFF44336);

    private long startMs = -1;

    public TrapTimerModule() {
        super("module.trap_timer.name", SoupBetterCategory.COMBAT);
        setup(trapType, startKey, resetKey, activeColor, expiredColor);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(startKey.getKey())) startMs = System.currentTimeMillis();
        if (e.isKeyDown(resetKey.getKey())) startMs = -1;
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (startMs < 0) return;
        MatrixStack m = e.getDrawContext().getMatrices();

        TrapType type = trapType.isSelected("dragon") ? TrapType.DRAGON : TrapType.NORMAL;
        long elapsed = System.currentTimeMillis() - startMs;
        long remaining = type.durationMs - elapsed;

        if (remaining <= 0) {
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "§c" + type.displayName + ": ИСТЕКЛА", 4, 50, expiredColor.getColor());
        } else {
            String time = String.format("%.1f", remaining / 1000.0);
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, type.displayName + ": §a" + time + "s", 4, 50, activeColor.getColor());
        }
    }
}
