package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.concurrent.ThreadLocalRandom;

public class MouseClickerModule extends Module {

    private final SelectSetting button = new SelectSetting(
            "mouse_clicker.button.name", "mouse_clicker.button.desc"
    ).value("left", "right").selected("left");

    private final ValueSetting cps = new ValueSetting(
            "mouse_clicker.cps.name", "mouse_clicker.cps.desc"
    ).range(1, 20).setValue(10.0f);

    private final BooleanSetting jitter = new BooleanSetting(
            "mouse_clicker.jitter.name", "mouse_clicker.jitter.desc"
    ).setValue(true);

    private final BooleanSetting requireHold = new BooleanSetting(
            "mouse_clicker.require_hold.name", "mouse_clicker.require_hold.desc"
    ).setValue(true);

    private final BooleanSetting onlyInGame = new BooleanSetting(
            "mouse_clicker.only_in_game.name", "mouse_clicker.only_in_game.desc"
    ).setValue(true);

    private long lastClickMs = 0;

    public MouseClickerModule() {
        super("module.mouse_clicker.name", SoupBetterCategory.GAMEPLAY);
        setup(button, cps, jitter, requireHold, onlyInGame);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        if (onlyInGame.isValue() && mc.currentScreen != null) return;

        boolean leftSel = button.isSelected("left");
        boolean rightSel = button.isSelected("right");

        // Если включена опция "только при удержании кнопки" — проверяем
        // что соответствующая клавиша игры реально нажата пользователем.
        if (requireHold.isValue()) {
            if (leftSel  && !mc.options.attackKey.isPressed()) return;
            if (rightSel && !mc.options.useKey   .isPressed()) return;
        }

        long now = System.currentTimeMillis();
        float effectiveCps = cps.getValue();
        if (jitter.isValue()) {
            // ±15% случайного дрожания, чтобы клики не были метрономно ровными
            float deviation = effectiveCps * 0.15f;
            effectiveCps += ThreadLocalRandom.current().nextFloat(-deviation, deviation);
            effectiveCps = Math.max(1f, effectiveCps);
        }
        long interval = (long) (1000.0f / effectiveCps);
        if (now - lastClickMs < interval) return;
        lastClickMs = now;

        if (leftSel) {
            mc.doAttack();
        } else {
            mc.doItemUse();
        }
    }

    @Override
    public void deactivate() {
        // Никаких глобальных побочных эффектов — модуль ничего не "залипает".
        lastClickMs = 0;
    }
}
