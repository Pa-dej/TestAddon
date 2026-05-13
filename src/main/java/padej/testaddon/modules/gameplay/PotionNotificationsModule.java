package padej.testaddon.modules.gameplay;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.List;

public class PotionNotificationsModule extends Module {

    private final ValueSetting timeThreshold = new ValueSetting(
            "potion_notif.time.name", "potion_notif.time.desc"
    ).range(3, 30).setValue(10.0f);

    private final BooleanSetting strength = new BooleanSetting(
            "potion_notif.strength.name", "potion_notif.strength.desc"
    ).setValue(true);

    private final BooleanSetting speed = new BooleanSetting(
            "potion_notif.speed.name", "potion_notif.speed.desc"
    ).setValue(true);

    private final BooleanSetting resistance = new BooleanSetting(
            "potion_notif.resistance.name", "potion_notif.resistance.desc"
    ).setValue(true);

    private long lastNotifyMs = 0;

    public PotionNotificationsModule() {
        super("module.potion_notifications.name", SoupBetterCategory.GAMEPLAY);
        setup(timeThreshold, strength, speed, resistance);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNotifyMs < 2000) return;

        int ticks = (int)(timeThreshold.getValue() * 20);
        List<RegistryEntry<StatusEffect>> toWatch = new java.util.ArrayList<>();
        if (strength.isValue())   toWatch.add(StatusEffects.STRENGTH);
        if (speed.isValue())      toWatch.add(StatusEffects.SPEED);
        if (resistance.isValue()) toWatch.add(StatusEffects.RESISTANCE);

        for (var effect : toWatch) {
            StatusEffectInstance inst = mc.player.getStatusEffect(effect);
            if (inst != null && inst.getDuration() <= ticks) {
                String name = effect.value().getName().getString();
                mc.player.sendMessage(
                        net.minecraft.text.Text.literal("§c[SoupBetter] §fЗаканчивается эффект: §e" + name),
                        false
                );
                lastNotifyMs = now;
                break;
            }
        }
    }
}
