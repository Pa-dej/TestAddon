package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.testaddon.SoupBetterCategory;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.AutoSprintModule}.
 *
 * <p>Семантика 1:1: пока модуль включён, постоянно держит «зажатой»
 * клавишу спринта и вызывает {@code player.setSprinting(true)} при наличии
 * forward-input. Оригинал также имел свитч {@code showHud} — он касался
 * внешнего HUD-отрисовщика, в SoupAPI модуль выводится в стандартном
 * списке модулей и эта опция уже не нужна.</p>
 */
public class AutoSprintModule extends Module {

    private final BooleanSetting sprintWhenSneaking = new BooleanSetting(
            "auto_sprint.sprint_sneak.name", "auto_sprint.sprint_sneak.desc"
    ).setValue(false);

    public AutoSprintModule() {
        super("module.auto_sprint.name", SoupBetterCategory.GAMEPLAY);
        setup(sprintWhenSneaking);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.options == null) return;
        var player = mc.player;

        // Постоянно держим клавишу спринта зажатой (как в оригинале)
        mc.options.sprintKey.setPressed(true);

        boolean canSprint = player.forwardSpeed > 0
                && !player.isUsingItem()
                && player.getHungerManager().getFoodLevel() > 6
                && (sprintWhenSneaking.isValue() || !player.isSneaking());

        if (canSprint && !player.isSprinting()) {
            player.setSprinting(true);
        }
    }
}
