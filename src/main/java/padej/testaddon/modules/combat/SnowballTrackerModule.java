package padej.testaddon.modules.combat;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;

import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.List;

public class SnowballTrackerModule extends Module {

    private final BooleanSetting onlyOwn = new BooleanSetting(
            "snowball_tracker.only_own.name", "snowball_tracker.only_own.desc"
    ).setValue(false);

    private final ColorSetting color = new ColorSetting(
            "snowball_tracker.color.name", "snowball_tracker.color.desc"
    ).value(0xFF00BCD4);

    private final List<SnowballEntity> tracked = new ArrayList<>();

    public SnowballTrackerModule() {
        super("module.snowball_tracker.name", SoupBetterCategory.COMBAT);
        setup(onlyOwn, color);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        tracked.clear();
        if (mc.world == null) return;

        mc.world.getEntities().forEach(entity -> {
            if (!(entity instanceof SnowballEntity sb)) return;
            if (onlyOwn.isValue() && mc.player != null && !mc.player.equals(sb.getOwner())) return;
            tracked.add(sb);
        });
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (tracked.isEmpty()) return;
        MatrixStack m = e.getDrawContext().getMatrices();
        int y = 4;
        for (SnowballEntity sb : tracked) {
            String owner = sb.getOwner() != null ? sb.getOwner().getName().getString() : "?";
            String pos = String.format("%.1f / %.1f / %.1f", sb.getX(), sb.getY(), sb.getZ());
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "⬤ " + owner + ": " + pos, 4, y, color.getColor());
            y += 12;
        }
    }
}
