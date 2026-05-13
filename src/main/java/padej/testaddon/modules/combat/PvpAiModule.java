package padej.testaddon.modules.combat;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;

import padej.testaddon.SoupBetterCategory;

import java.util.*;

public class PvpAiModule extends Module {

    private final BooleanSetting showDamage = new BooleanSetting(
            "pvp_ai.damage.name", "pvp_ai.damage.desc"
    ).setValue(true);

    private final BooleanSetting showArmor = new BooleanSetting(
            "pvp_ai.armor.name", "pvp_ai.armor.desc"
    ).setValue(true);

    private final BooleanSetting showTotem = new BooleanSetting(
            "pvp_ai.totem.name", "pvp_ai.totem.desc"
    ).setValue(true);

    private final ColorSetting headerColor = new ColorSetting(
            "pvp_ai.header_color.name", "pvp_ai.header_color.desc"
    ).value(0xFFFF5722);

    private PlayerEntity target = null;
    private final List<String> lines = new ArrayList<>();

    public PvpAiModule() {
        super("module.pvp_ai.name", SoupBetterCategory.COMBAT);
        setup(showDamage, showArmor, showTotem, headerColor);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        lines.clear();
        target = getClosestEnemy();
        if (target == null) return;

        if (showArmor.isValue()) {
            int armorVal = target.getArmor();
            int armorDurPct = getArmorDurabilityPct(target);
            lines.add("§7Броня: §f" + armorVal + " §7(" + armorDurPct + "%)");
        }
        if (showTotem.isValue()) {
            boolean hasTotem = target.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                    || target.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
            lines.add("§7Тотем: " + (hasTotem ? "§aДА" : "§cНЕТ"));
        }
        if (showDamage.isValue()) {
            float estimated = estimateDamage(target);
            lines.add("§7Урон ~: §c" + String.format("%.1f", estimated) + " ❤");
        }
        if (target.hasStatusEffect(StatusEffects.STRENGTH)) {
            lines.add("§7Сила: §c+" + (target.getStatusEffect(StatusEffects.STRENGTH).getAmplifier() + 1));
        }
    }

    private PlayerEntity getClosestEnemy() {
        if (mc.world == null || mc.player == null) return null;
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && !p.isDead())
                .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(mc.player)))
                .orElse(null);
    }

    private int getArmorDurabilityPct(PlayerEntity p) {
        int total = 0, count = 0;
        for (ItemStack s : p.getArmorItems()) {
            if (!s.isEmpty() && s.getMaxDamage() > 0) {
                total += (int)(100f * (1f - (float)s.getDamage() / s.getMaxDamage()));
                count++;
            }
        }
        return count == 0 ? 100 : total / count;
    }

    private float estimateDamage(LivingEntity target) {
        if (mc.player == null) return 0;
        ItemStack weapon = mc.player.getMainHandStack();
        float base = weapon.isEmpty() ? 1f
                : (float) mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        float armor = target.getArmor();
        float reduction = armor / (armor + 8f);
        return base * (1f - reduction);
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (target == null || lines.isEmpty()) return;
        MatrixStack m = e.getDrawContext().getMatrices();
        int x = e.getDrawContext().getScaledWindowWidth() - 120;
        int y = 4;
        Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "§lPvP AI: §r" + target.getName().getString(), x, y, headerColor.getColor());
        y += 14;
        for (String line : lines) {
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, line, x, y, 0xFFFFFFFF);
            y += 11;
        }
    }
}
