package padej.testaddon.modules.gameplay;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;

import padej.soup.api.system.font.Fonts;
import padej.testaddon.SoupBetterCategory;

public class CoordinateHelperModule extends Module {

    private final SelectSetting style = new SelectSetting(
            "coord.style.name", "coord.style.desc"
    ).value("simple", "detailed").selected("simple");

    private final BooleanSetting showDimension = new BooleanSetting(
            "coord.dim.name", "coord.dim.desc"
    ).setValue(true);

    private final ColorSetting textColor = new ColorSetting(
            "coord.color.name", "coord.color.desc"
    ).value(0xFFFFFFFF);

    public CoordinateHelperModule() {
        super("module.coordinate_helper.name", SoupBetterCategory.GAMEPLAY);
        setup(style, showDimension, textColor);
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (mc.player == null) return;

        BlockPos pos = mc.player.getBlockPos();
        int color = textColor.getColor();
        MatrixStack matrices = e.getDrawContext().getMatrices();
        var font = Fonts.getSize(12, Fonts.Type.INTER_BOLD);

        if (style.isSelected("simple")) {
            String text = pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
            font.drawString(matrices, text, 4, 4, color);
        } else {
            font.drawString(matrices, "X: " + pos.getX(), 4, 4, color);
            font.drawString(matrices, "Y: " + pos.getY(), 4, 14, color);
            font.drawString(matrices, "Z: " + pos.getZ(), 4, 24, color);
            if (showDimension.isValue() && mc.world != null) {
                String dim = mc.world.getRegistryKey().getValue().getPath();
                font.drawString(matrices, "D: " + dim, 4, 34, color);
            }
        }
    }
}
