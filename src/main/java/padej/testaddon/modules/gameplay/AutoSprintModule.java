package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.testaddon.SoupBetterCategory;

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
        if (mc.player == null) return;
        var player = mc.player;

        boolean canSprint = player.forwardSpeed > 0
                && !player.isUsingItem()
                && player.getHungerManager().getFoodLevel() > 6
                && (sprintWhenSneaking.isValue() || !player.isSneaking());

        if (canSprint && !player.isSprinting()) {
            player.setSprinting(true);
        }
    }
}
