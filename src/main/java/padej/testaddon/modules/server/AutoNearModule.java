package padej.testaddon.modules.server;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

public class AutoNearModule extends Module {

    private final TextSetting command = new TextSetting(
            "auto_near.command.name", "auto_near.command.desc"
    ).setText("/near max");

    private final ValueSetting delaySeconds = new ValueSetting(
            "auto_near.delay.name", "auto_near.delay.desc"
    ).range(5, 120).setValue(30.0f);

    private long lastCommandMs = 0;

    public AutoNearModule() {
        super("module.auto_near.name", SoupBetterCategory.SERVER);
        setup(command, delaySeconds);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        long delay = (long)(delaySeconds.getValue() * 1000);
        if (now - lastCommandMs < delay) return;
        lastCommandMs = now;

        String cmd = command.getText().trim();
        if (cmd.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(cmd.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(cmd);
        }
    }
}
