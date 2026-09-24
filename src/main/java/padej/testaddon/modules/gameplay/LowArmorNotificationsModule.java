package padej.testaddon.modules.gameplay;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.LowArmorNotificationsModule}.
 *
 * <p><b>Назначение</b>: per-slot уведомления о низкой прочности доспеха.
 * Каждая часть (шлем/нагрудник/штаны/ботинки) может быть включена отдельно,
 * каждая имеет свой {@code lastNotifyTime} с интервалом подавления повторных
 * сообщений. Когда несколько частей разом ниже порога — единая «групповая»
 * нотификация со списком.</p>
 *
 * <p>Порядок слотов в {@code getArmorItems()} (yarn 1.21.x): 0=ботинки,
 * 1=штаны, 2=нагрудник, 3=шлем — оригинал так считает.</p>
 */
public class LowArmorNotificationsModule extends Module {

    private final ValueSetting threshold = new ValueSetting(
            "low_armor.threshold.name", "low_armor.threshold.desc"
    ).range(1, 99).setValue(20.0f).setInteger(true);

    private final ValueSetting intervalSeconds = new ValueSetting(
            "low_armor.interval.name", "low_armor.interval.desc"
    ).range(5, 120).setValue(30.0f).setInteger(true);

    private final BooleanSetting helmet     = new BooleanSetting(
            "low_armor.helmet.name", "low_armor.helmet.desc").setValue(true);
    private final BooleanSetting chestplate = new BooleanSetting(
            "low_armor.chestplate.name", "low_armor.chestplate.desc").setValue(true);
    private final BooleanSetting leggings   = new BooleanSetting(
            "low_armor.leggings.name", "low_armor.leggings.desc").setValue(true);
    private final BooleanSetting boots      = new BooleanSetting(
            "low_armor.boots.name", "low_armor.boots.desc").setValue(true);

    /** [0..3] — per-slot last notify timestamps, как в оригинале. */
    private final long[] lastNotifyTime = new long[4];
    private long lastGroupNotifyTime;

    public LowArmorNotificationsModule() {
        super("module.low_armor_notifications.name", SoupBetterCategory.GAMEPLAY);
        setup(threshold, intervalSeconds, helmet, chestplate, leggings, boots);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || mc.player.age % 20 != 0) return;

        long now = System.currentTimeMillis();
        long intervalMs = Math.max(5L, (long) intervalSeconds.getValue()) * 1000L;
        int thresholdPct = Math.max(1, (int) threshold.getValue());
        List<Integer> lowPieces = new ArrayList<>();

        for (int slot = 0; slot < 4; slot++) {
            ItemStack armorStack = mc.player.getInventory().getArmorStack(slot);
            if (armorStack.isEmpty() || !armorStack.isDamageable() || !shouldCheck(slot)) {
                lastNotifyTime[slot] = 0L;
                continue;
            }
            int remaining = armorStack.getMaxDamage() - armorStack.getDamage();
            float percent = remaining * 100.0f / armorStack.getMaxDamage();
            if (percent <= thresholdPct) {
                lowPieces.add(slot);
            } else {
                lastNotifyTime[slot] = 0L;
            }
        }

        if (lowPieces.isEmpty()) return;

        List<Integer> freshPieces = new ArrayList<>();
        for (int slot : lowPieces) {
            if (now - lastNotifyTime[slot] >= intervalMs) freshPieces.add(slot);
        }
        if (freshPieces.isEmpty() || now - lastGroupNotifyTime < intervalMs) return;

        StringBuilder builder = new StringBuilder("Low armor: ");
        for (int i = 0; i < lowPieces.size(); i++) {
            builder.append(getArmorName(lowPieces.get(i)));
            if (i < lowPieces.size() - 1) builder.append(", ");
        }
        logDirect(Text.literal(builder.toString()).formatted(Formatting.RED));

        for (int slot : lowPieces) lastNotifyTime[slot] = now;
        lastGroupNotifyTime = now;
    }

    private boolean shouldCheck(int slot) {
        return switch (slot) {
            case 0 -> boots.isValue();
            case 1 -> leggings.isValue();
            case 2 -> chestplate.isValue();
            case 3 -> helmet.isValue();
            default -> false;
        };
    }

    private String getArmorName(int slot) {
        return switch (slot) {
            case 0 -> "boots";
            case 1 -> "leggings";
            case 2 -> "chestplate";
            case 3 -> "helmet";
            default -> "armor";
        };
    }
}
