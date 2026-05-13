package padej.testaddon.modules.combat;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;

import padej.testaddon.SoupBetterCategory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TotemTrackerModule extends Module {

    private final BooleanSetting showPopCount = new BooleanSetting(
            "totem_tracker.pop_count.name", "totem_tracker.pop_count.desc"
    ).setValue(true);

    private final ValueSetting displayDuration = new ValueSetting(
            "totem_tracker.duration.name", "totem_tracker.duration.desc"
    ).range(1, 15).setValue(5.0f);

    private final ColorSetting textColor = new ColorSetting(
            "totem_tracker.color.name", "totem_tracker.color.desc"
    ).value(0xFFFFD700);

    // UUID → кол-во тотемов в руке (предыдущий тик)
    private final Map<UUID, Integer> prevTotems = new ConcurrentHashMap<>();
    // UUID → кол-во попов
    private final Map<UUID, Integer> popCounts  = new ConcurrentHashMap<>();

    private record Notification(String playerName, long expireMs) {}
    private final List<Notification> notifications = new ArrayList<>();

    public TotemTrackerModule() {
        super("module.totem_tracker.name", SoupBetterCategory.COMBAT);
        setup(showPopCount, displayDuration, textColor);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.world == null) return;
        long now = System.currentTimeMillis();

        for (PlayerEntity player : mc.world.getPlayers()) {
            UUID id = player.getUuid();
            int curTotems = countTotems(player);
            Integer prev = prevTotems.get(id);

            if (prev != null && curTotems < prev) {
                // Тотем был использован
                popCounts.merge(id, 1, Integer::sum);
                long dur = (long)(displayDuration.getValue() * 1000);
                notifications.add(new Notification(player.getName().getString(), now + dur));
            }
            prevTotems.put(id, curTotems);
        }
        notifications.removeIf(n -> now > n.expireMs);
    }

    private int countTotems(PlayerEntity player) {
        int count = 0;
        if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) count++;
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))  count++;
        return count;
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (notifications.isEmpty()) return;
        MatrixStack m = e.getDrawContext().getMatrices();
        int y = 4;
        for (Notification n : notifications) {
            String text = n.playerName + " §cпоп!";
            if (showPopCount.isValue()) {
                UUID id = mc.world.getPlayers().stream()
                        .filter(p -> p.getName().getString().equals(n.playerName))
                        .map(PlayerEntity::getUuid).findFirst().orElse(null);
                if (id != null) text += " §7(x" + popCounts.getOrDefault(id, 1) + ")";
            }
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, text, 4, y, textColor.getColor());
            y += 12;
        }
    }
}
