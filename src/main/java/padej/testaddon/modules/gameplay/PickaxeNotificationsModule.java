package padej.testaddon.modules.gameplay;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

public class PickaxeNotificationsModule extends Module {

    private final ValueSetting threshold = new ValueSetting(
            "pickaxe_notif.threshold.name", "pickaxe_notif.threshold.desc"
    ).range(1, 50).setValue(10.0f);

    private long lastNotifyMs = 0;

    public PickaxeNotificationsModule() {
        super("module.pickaxe_notifications.name", SoupBetterCategory.GAMEPLAY);
        setup(threshold);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNotifyMs < 10000) return;

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || s.getMaxDamage() == 0) continue;
            if (!s.isOf(Items.NETHERITE_PICKAXE) && !s.isOf(Items.DIAMOND_PICKAXE)) continue;
            int pct = (int)(100f * (1f - (float)s.getDamage() / s.getMaxDamage()));
            if (pct <= (int)threshold.getValue()) {
                mc.player.sendMessage(
                        net.minecraft.text.Text.literal("§c[SoupBetter] §fНизкая прочность кирки: §e" + pct + "%"),
                        false
                );
                lastNotifyMs = now;
                break;
            }
        }
    }
}
