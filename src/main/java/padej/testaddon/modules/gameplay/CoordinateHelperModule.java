package padej.testaddon.modules.gameplay;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.testaddon.SoupBetterCategory;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.CoordinateHelperModule}.
 *
 * <p><b>Назначение</b>: по горячей клавише отправляет в чат серверную команду
 * {@code /m <target> X Y Z}, передавая указанному игроку текущие координаты.
 * На FunTime/AresMine это «пригласить в координаты». Это <i>не</i> HUD-индикатор.</p>
 */
public class CoordinateHelperModule extends Module {

    private final BindSetting key = new BindSetting(
            "coord.key.name", "coord.key.desc"
    ).setKey(GLFW.GLFW_KEY_U);

    private final TextSetting targetName = new TextSetting(
            "coord.target.name", "coord.target.desc"
    ).setText("").setMax(32);

    private long lastSendMs = 0;

    public CoordinateHelperModule() {
        super("module.coordinate_helper.name", SoupBetterCategory.GAMEPLAY);
        setup(key, targetName);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        int k = key.getKey();
        if (k == GLFW.GLFW_KEY_UNKNOWN || mc.player == null) return;
        if (mc.currentScreen != null) return;
        if (!e.isKeyDown(k)) return;
        sendCoordinates();
    }

    private void sendCoordinates() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        String target = targetName.getText() == null ? "" : targetName.getText().trim();
        if (target.length() < 3) {
            logDirect(Text.literal("Set a target nickname for Coordinate Helper").formatted(Formatting.RED));
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSendMs < 400L) return;

        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());

        mc.getNetworkHandler().sendChatCommand("m " + target + " " + x + " " + y + " " + z);
        lastSendMs = now;
        logDirect(Text.literal("Sent coordinates to " + target).formatted(Formatting.GREEN));
    }
}
