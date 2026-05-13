package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;
import org.lwjgl.glfw.GLFW;

public class MouseClickerModule extends Module {

    private final SelectSetting button = new SelectSetting(
            "mouse_clicker.button.name", "mouse_clicker.button.desc"
    ).value("left", "right").selected("left");

    private final ValueSetting cps = new ValueSetting(
            "mouse_clicker.cps.name", "mouse_clicker.cps.desc"
    ).range(1, 20).setValue(10.0f);

    private final BooleanSetting onlyInGame = new BooleanSetting(
            "mouse_clicker.only_in_game.name", "mouse_clicker.only_in_game.desc"
    ).setValue(true);

    private long lastClickMs = 0;

    public MouseClickerModule() {
        super("module.mouse_clicker.name", SoupBetterCategory.GAMEPLAY);
        setup(button, cps, onlyInGame);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        if (onlyInGame.isValue() && mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        long interval = (long)(1000.0f / cps.getValue());
        if (now - lastClickMs < interval) return;
        lastClickMs = now;

        long win = mc.getWindow().getHandle();
        int btn = button.isSelected("right") ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_LEFT;
        GLFW.glfwSetMouseButtonCallback(win, null);
        // Отправляем синтетический клик через опции мыши
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            mc.options.attackKey.setPressed(true);
        } else {
            mc.options.useKey.setPressed(true);
        }
    }

    @Override
    public void deactivate() {
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
    }
}
