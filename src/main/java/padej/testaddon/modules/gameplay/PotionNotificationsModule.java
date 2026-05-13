package padej.testaddon.modules.gameplay;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.MultiSelectSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.PotionNotificationsModule}.
 *
 * <p><b>Назначение</b>: предупреждает за N секунд до окончания серверных
 * «bundle»-зелий (FunTime/AresMine), где каждый бандл — AND-комбинация
 * нескольких эффектов с заданными уровнями (амплитудами).</p>
 *
 * <p>Семь бандлов: Assassin, Paladin, Wrath, Sleeping, Radiation, Firecracker, Holy Water.
 * Проверки уровней — 1:1 как в оригинале.</p>
 *
 * <p>Это <i>не</i> уведомление по одиночным ванильным эффектам.</p>
 */
public class PotionNotificationsModule extends Module {

    private static final String[] BUNDLES = {
            "Assassin", "Paladin", "Wrath", "Sleeping", "Radiation", "Firecracker", "Holy Water"
    };

    private final MultiSelectSetting enabledBundles = new MultiSelectSetting(
            "potion_notif.bundles.name", "potion_notif.bundles.desc"
    ).value(BUNDLES).selected(BUNDLES);

    private final ValueSetting warnBefore = new ValueSetting(
            "potion_notif.warn_before.name", "potion_notif.warn_before.desc"
    ).range(1, 30).setValue(10.0f).setInteger(true);

    private final Set<String> notifiedBundles = new HashSet<>();
    private long lastNotifyMs;

    public PotionNotificationsModule() {
        super("module.potion_notifications.name", SoupBetterCategory.GAMEPLAY);
        setup(enabledBundles, warnBefore);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || mc.player.age % 10 != 0) return;

        List<String> activeBundles = new ArrayList<>();
        List<String> expiringBundles = new ArrayList<>();
        int warnSeconds = Math.max(1, (int) warnBefore.getValue());
        long now = System.currentTimeMillis();

        inspectBundle("Assassin",
                enabledBundles.isSelected("Assassin"),
                isAssassinActive(),
                getMinDuration(StatusEffects.STRENGTH, StatusEffects.SPEED, StatusEffects.HASTE),
                warnSeconds, activeBundles, expiringBundles);

        inspectBundle("Paladin",
                enabledBundles.isSelected("Paladin"),
                isPaladinActive(),
                getMinDuration(StatusEffects.RESISTANCE, StatusEffects.FIRE_RESISTANCE,
                        StatusEffects.INVISIBILITY, StatusEffects.HEALTH_BOOST),
                warnSeconds, activeBundles, expiringBundles);

        inspectBundle("Wrath",
                enabledBundles.isSelected("Wrath"),
                isWrathActive(),
                getMinDuration(StatusEffects.STRENGTH, StatusEffects.SLOWNESS),
                warnSeconds, activeBundles, expiringBundles);

        inspectBundle("Sleeping",
                enabledBundles.isSelected("Sleeping"),
                isSleepingActive(),
                getMinDuration(StatusEffects.WEAKNESS, StatusEffects.MINING_FATIGUE,
                        StatusEffects.WITHER, StatusEffects.BLINDNESS),
                warnSeconds, activeBundles, expiringBundles);

        inspectBundle("Radiation",
                enabledBundles.isSelected("Radiation"),
                isRadiationActive(),
                getMinDuration(StatusEffects.POISON, StatusEffects.WITHER,
                        StatusEffects.SLOWNESS, StatusEffects.HUNGER, StatusEffects.GLOWING),
                warnSeconds, activeBundles, expiringBundles);

        inspectBundle("Firecracker",
                enabledBundles.isSelected("Firecracker"),
                isFirecrackerActive(),
                getMinDuration(StatusEffects.SLOWNESS, StatusEffects.SPEED,
                        StatusEffects.BLINDNESS, StatusEffects.GLOWING),
                warnSeconds, activeBundles, expiringBundles);

        inspectBundle("Holy Water",
                enabledBundles.isSelected("Holy Water"),
                isHolyWaterActive(),
                getMinDuration(StatusEffects.REGENERATION, StatusEffects.INVISIBILITY),
                warnSeconds, activeBundles, expiringBundles);

        notifiedBundles.removeIf(name -> !activeBundles.contains(name));

        if (!expiringBundles.isEmpty() && now - lastNotifyMs >= warnSeconds * 1000L) {
            String message = "Potions expiring soon: " + String.join(", ", expiringBundles);
            logDirect(Text.literal(message).formatted(Formatting.GOLD));
            notifiedBundles.addAll(expiringBundles);
            lastNotifyMs = now;
        }
    }

    private void inspectBundle(String name, boolean settingEnabled, boolean active, int minDurationTicks,
                               int warnSeconds, List<String> activeBundles, List<String> expiringBundles) {
        if (!settingEnabled || !active) {
            notifiedBundles.remove(name);
            return;
        }
        activeBundles.add(name);
        int secondsLeft = minDurationTicks / 20;
        if (secondsLeft > warnSeconds) {
            notifiedBundles.remove(name);
            return;
        }
        if (!notifiedBundles.contains(name)) {
            expiringBundles.add(name);
        }
    }

    // ---------------- bundle predicates (1:1 с оригиналом) ----------------

    private boolean isAssassinActive() {
        return amp(StatusEffects.STRENGTH) >= 3 && amp(StatusEffects.SPEED) >= 2 && has(StatusEffects.HASTE);
    }

    private boolean isPaladinActive() {
        return has(StatusEffects.RESISTANCE)
                && has(StatusEffects.FIRE_RESISTANCE)
                && has(StatusEffects.INVISIBILITY)
                && amp(StatusEffects.HEALTH_BOOST) >= 2;
    }

    private boolean isWrathActive() {
        return amp(StatusEffects.STRENGTH) >= 4 && amp(StatusEffects.SLOWNESS) >= 3;
    }

    private boolean isSleepingActive() {
        return amp(StatusEffects.WEAKNESS) >= 1
                && amp(StatusEffects.MINING_FATIGUE) >= 1
                && amp(StatusEffects.WITHER) >= 2
                && has(StatusEffects.BLINDNESS);
    }

    private boolean isRadiationActive() {
        return amp(StatusEffects.POISON) >= 1
                && amp(StatusEffects.WITHER) >= 1
                && amp(StatusEffects.SLOWNESS) >= 2
                && amp(StatusEffects.HUNGER) >= 4
                && has(StatusEffects.GLOWING);
    }

    private boolean isFirecrackerActive() {
        return amp(StatusEffects.SLOWNESS) >= 9
                && amp(StatusEffects.SPEED) >= 4
                && amp(StatusEffects.BLINDNESS) >= 9
                && has(StatusEffects.GLOWING);
    }

    private boolean isHolyWaterActive() {
        return amp(StatusEffects.REGENERATION) >= 2 && has(StatusEffects.INVISIBILITY);
    }

    @SafeVarargs
    private int getMinDuration(RegistryEntry<StatusEffect>... effects) {
        int minDuration = Integer.MAX_VALUE;
        for (RegistryEntry<StatusEffect> effect : effects) {
            StatusEffectInstance instance = mc.player.getStatusEffect(effect);
            if (instance == null || instance.isInfinite()) return 0;
            minDuration = Math.min(minDuration, instance.getDuration());
        }
        return minDuration == Integer.MAX_VALUE ? 0 : minDuration;
    }

    private boolean has(RegistryEntry<StatusEffect> effect) {
        return mc.player != null && mc.player.hasStatusEffect(effect);
    }

    private int amp(RegistryEntry<StatusEffect> effect) {
        if (mc.player == null) return -1;
        StatusEffectInstance instance = mc.player.getStatusEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }
}
