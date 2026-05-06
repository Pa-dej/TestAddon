package padej.testaddon;

import net.minecraft.client.MinecraftClient;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.event.types.Priority;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;

public class HudInfoModule extends Module {

    // ── Настройки ──────────────────────────────────────────────────────────────

    private final BooleanSetting showFps = new BooleanSetting("showfps", "desc")
            .setValue(true);

    private final BooleanSetting showCoords = new BooleanSetting("showcoords", "desc")
            .setValue(true);

    private final SelectSetting style = new SelectSetting("style", "desc")
            .value("Compact", "Detailed")
            .selected("Compact");

    // ── Состояние ─────────────────────────────────────────────────────────────

    private int fps = 0;

    // ── Конструктор ────────────────────────────────────────────────────────────

    public HudInfoModule() {
        super("module.hud_info.name", TestAddonCategory.MAIN);
        setup(showFps, showCoords, style);
    }

    // ── Обработчики событий ────────────────────────────────────────────────────

    @EventHandler(Priority.MEDIUM)
    public void onDraw(DrawEvent e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int x = 4;
        int y = 4;

        if (showFps.isValue()) {
            fps = mc.getCurrentFps();
            String fpsText = style.isSelected("Compact")
                    ? "FPS: " + fps
                    : "FPS: " + fps + " (" + (fps >= 60 ? "Good" : fps >= 30 ? "OK" : "Low") + ")";
            e.getDrawContext().drawTextWithShadow(
                    mc.textRenderer, fpsText, x, y, 0xFFFFFFFF
            );
            y += 10;
        }

        if (showCoords.isValue()) {
            int px = (int) mc.player.getX();
            int py = (int) mc.player.getY();
            int pz = (int) mc.player.getZ();
            String coordText = style.isSelected("Compact")
                    ? px + " / " + py + " / " + pz
                    : "X: " + px + "  Y: " + py + "  Z: " + pz;
            e.getDrawContext().drawTextWithShadow(
                    mc.textRenderer, coordText, x, y, 0xFFFFFFFF
            );
        }
    }
}
