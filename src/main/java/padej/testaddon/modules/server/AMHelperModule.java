package padej.testaddon.modules.server;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;

import padej.testaddon.SoupBetterCategory;

public class AMHelperModule extends Module {

    private final TextSetting itemName = new TextSetting(
            "am_helper.item.name", "am_helper.item.desc"
    ).setText("жимка");

    private final ColorSetting color = new ColorSetting(
            "am_helper.color.name", "am_helper.color.desc"
    ).value(0xFFFF9800);

    public AMHelperModule() {
        super("module.am_helper.name", SoupBetterCategory.SERVER);
        setup(itemName, color);
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (mc.player == null) return;
        ItemStack main = mc.player.getMainHandStack();
        if (main.isEmpty()) return;

        String held = main.getName().getString().toLowerCase();
        if (!held.contains(itemName.getText().toLowerCase())) return;

        MatrixStack m = e.getDrawContext().getMatrices();
        int cx = e.getDrawContext().getScaledWindowWidth() / 2;
        int cy = e.getDrawContext().getScaledWindowHeight() / 2;
        Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "▶ ТРАПКА ◀", cx - 20, cy - 30, color.getColor());
    }
}
