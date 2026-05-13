package padej.testaddon.modules.server;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;

import padej.testaddon.SoupBetterCategory;

public class EventDelayModule extends Module {

    private final ValueSetting delaySeconds = new ValueSetting(
            "event_delay.delay.name", "event_delay.delay.desc"
    ).range(1, 60).setValue(10.0f);

    private final TextSetting command = new TextSetting(
            "event_delay.command.name", "event_delay.command.desc"
    ).setText("/event join");

    private final BindSetting startKey = new BindSetting(
            "event_delay.start.name", "event_delay.start.desc"
    );

    private final BindSetting cancelKey = new BindSetting(
            "event_delay.cancel.name", "event_delay.cancel.desc"
    );

    private final ColorSetting timerColor = new ColorSetting(
            "event_delay.color.name", "event_delay.color.desc"
    ).value(0xFF03A9F4);

    private long executeAtMs = -1;

    public EventDelayModule() {
        super("module.event_delay.name", SoupBetterCategory.SERVER);
        setup(delaySeconds, command, startKey, cancelKey, timerColor);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(startKey.getKey())) {
            executeAtMs = System.currentTimeMillis() + (long)(delaySeconds.getValue() * 1000);
        }
        if (e.isKeyDown(cancelKey.getKey())) {
            executeAtMs = -1;
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (executeAtMs < 0) return;

        long remaining = executeAtMs - System.currentTimeMillis();

        if (remaining <= 0) {
            fireCommand();
            executeAtMs = -1;
            return;
        }

        MatrixStack m = e.getDrawContext().getMatrices();
        int cx = e.getDrawContext().getScaledWindowWidth() / 2;
        int cy = e.getDrawContext().getScaledWindowHeight() / 2;
        String text = String.format("Ивент через: §l%.1fs", remaining / 1000.0);
        Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, text, cx - 40, cy - 40, timerColor.getColor());
    }

    private void fireCommand() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        String cmd = command.getText().trim();
        if (cmd.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(cmd.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(cmd);
        }
    }
}
