package padej.testaddon.modules.gameplay;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

public class LowArmorNotificationsModule extends Module {

    private final ValueSetting threshold = new ValueSetting(
            "low_armor.threshold.name", "low_armor.threshold.desc"
    ).range(1, 50).setValue(20.0f);

    private final BooleanSetting onlyHeld = new BooleanSetting(
            "low_armor.only_held.name", "low_armor.only_held.desc"
    ).setValue(false);

    private long lastNotifyMs = 0;

    public LowArmorNotificationsModule() {
        super("module.low_armor_notifications.name", SoupBetterCategory.GAMEPLAY);
        setup(threshold, onlyHeld);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNotifyMs < 5000) return;

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack s = mc.player.getEquippedStack(slot);
            if (s.isEmpty() || s.getMaxDamage() == 0) continue;
            int pct = (int)(100f * (1f - (float)s.getDamage() / s.getMaxDamage()));
            if (pct <= (int)threshold.getValue()) {
                mc.player.sendMessage(
                        net.minecraft.text.Text.literal("§c[SoupBetter] §fНизкая прочность брони: §e" + slot.getName() + " §f(" + pct + "%)"),
                        false
                );
                lastNotifyMs = now;
                break;
            }
        }
    }
}
