package padej.testaddon.modules.combat;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;

import padej.testaddon.SoupBetterCategory;

import java.util.*;

public class PredictionsModule extends Module {

    private final ValueSetting reach = new ValueSetting(
            "predictions.reach.name", "predictions.reach.desc"
    ).range(2.0f, 6.0f).setValue(3.0f);

    private final BooleanSetting showHitChance = new BooleanSetting(
            "predictions.hit_chance.name", "predictions.hit_chance.desc"
    ).setValue(true);

    private final BooleanSetting showDistance = new BooleanSetting(
            "predictions.distance.name", "predictions.distance.desc"
    ).setValue(true);

    private final ColorSetting hitColor  = new ColorSetting(
            "predictions.hit_color.name", "predictions.hit_color.desc"
    ).value(0xFF4CAF50);

    private final ColorSetting missColor = new ColorSetting(
            "predictions.miss_color.name", "predictions.miss_color.desc"
    ).value(0xFFF44336);

    private PlayerEntity closestTarget = null;
    private float hitChance = 0;
    private float distance = 0;

    public PredictionsModule() {
        super("module.predictions.name", SoupBetterCategory.COMBAT);
        setup(reach, showHitChance, showDistance, hitColor, missColor);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        closestTarget = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && !p.isDead())
                .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(mc.player)))
                .orElse(null);

        if (closestTarget == null) return;

        distance = (float) mc.player.distanceTo(closestTarget);
        hitChance = calcHitChance(closestTarget);
    }

    private float calcHitChance(PlayerEntity target) {
        if (mc.player == null) return 0;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(reach.getValue()));

        Box targetBox = target.getBoundingBox().expand(0.1);
        var hit = targetBox.raycast(eye, end);
        if (hit.isEmpty()) {
            // Приблизительный шанс на основе дистанции и угла
            Vec3d toTarget = target.getPos().subtract(eye).normalize();
            double dot = look.dotProduct(toTarget);
            return (float) Math.max(0, dot * (1.0 - distance / reach.getValue()));
        }
        return 1.0f;
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (closestTarget == null) return;
        MatrixStack m = e.getDrawContext().getMatrices();
        int x = 4;
        int y = e.getDrawContext().getScaledWindowHeight() / 2 + 20;

        if (showHitChance.isValue()) {
            int pct = (int)(hitChance * 100);
            int col = hitChance > 0.7f ? hitColor.getColor() : missColor.getColor();
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "Попадание: " + pct + "%", x, y, col);
            y += 12;
        }
        if (showDistance.isValue()) {
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "Дист: " + String.format("%.2f", distance) + "m", x, y, 0xFFFFFFFF);
        }
    }
}
