package padej.testaddon.modules.combat;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.EventAttack;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.soup.api.system.font.Fonts;
import padej.testaddon.SoupBetterCategory;
import padej.testaddon.util.ServerUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.TotemTrackerModule}.
 *
 * <p><b>Назначение</b>: считает «попы» тотемов <i>только тех игроков, которых
 * мы атаковали</i> в течение последних {@link #ATTACK_TIMEOUT} мс. Это даёт
 * чистый счётчик «моих» киллов без спама от драк на расстоянии.</p>
 *
 * <p>Сохранены: 10-секундный сброс счётчика, чтение кастомного имени тотема
 * (offhand → mainhand → fallback), фильтр {@code isFunTimeServer()}.</p>
 */
public class TotemTrackerModule extends Module {

    /** Окно «наш килл»: тотем должен поппнуться в течение 1 секунды после нашего удара. */
    private static final long ATTACK_TIMEOUT = 1000L;
    /** Если за это время от прошлого попа прошло больше — счётчик попов сбрасывается в 0. */
    private static final long POP_RESET_TIME = 10_000L;

    private final BooleanSetting showPopCount = new BooleanSetting(
            "totem_tracker.pop_count.name", "totem_tracker.pop_count.desc"
    ).setValue(true);

    private final BooleanSetting onlyFunTime = new BooleanSetting(
            "totem_tracker.fun_time.name", "totem_tracker.fun_time.desc"
    ).setValue(true);

    private final ValueSetting displayDuration = new ValueSetting(
            "totem_tracker.duration.name", "totem_tracker.duration.desc"
    ).range(1, 15).setValue(5.0f);

    private final ColorSetting textColor = new ColorSetting(
            "totem_tracker.color.name", "totem_tracker.color.desc"
    ).value(0xFFFFD700);

    /** UUID → количество тотемов в руках в предыдущем тике. */
    private final Map<UUID, Integer> prevTotems = new ConcurrentHashMap<>();
    /** Имя игрока → счётчик попов. */
    private final Map<String, Integer> playerPopCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPopTime = new ConcurrentHashMap<>();

    private PlayerEntity lastAttackedPlayer = null;
    private long lastAttackTime = 0;

    private final List<TotemNotification> notifications = new ArrayList<>();

    public TotemTrackerModule() {
        super("module.totem_tracker.name", SoupBetterCategory.COMBAT);
        setup(showPopCount, onlyFunTime, displayDuration, textColor);
    }

    /** Запоминаем нашу атаку — нужно для isOurKill. */
    @EventHandler
    public void onAttack(EventAttack e) {
        if (mc.player == null) return;
        if (onlyFunTime.isValue() && !ServerUtil.isFunTimeServer()) return;
        Entity target = e.getTarget();
        if (target instanceof PlayerEntity player) {
            lastAttackedPlayer = player;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.world == null || mc.player == null) return;
        if (onlyFunTime.isValue() && !ServerUtil.isFunTimeServer()) return;

        long now = System.currentTimeMillis();

        // Сканируем тотемы в руках у всех игроков; поп = уменьшение количества.
        for (PlayerEntity player : mc.world.getPlayers()) {
            UUID id = player.getUuid();
            int curTotems = countTotems(player);
            Integer prev = prevTotems.get(id);

            if (prev != null && curTotems < prev) {
                handleTotemPop(player, now);
            }
            prevTotems.put(id, curTotems);
        }

        // Удаляем нотификации с истёкшим сроком.
        long durationMs = (long) (displayDuration.getValue() * 1000);
        Iterator<TotemNotification> it = notifications.iterator();
        while (it.hasNext()) {
            TotemNotification n = it.next();
            if (now - n.timestamp > durationMs) it.remove();
        }

        // Чистим устаревший pop-state.
        lastPopTime.entrySet().removeIf(entry -> now - entry.getValue() > POP_RESET_TIME * 2);
        playerPopCounts.keySet().removeIf(k -> !lastPopTime.containsKey(k));
    }

    private void handleTotemPop(PlayerEntity player, long now) {
        // Только если это наш килл (мы били этого игрока в последнюю секунду).
        if (player == mc.player) return;
        if (lastAttackedPlayer == null) return;
        if (lastAttackedPlayer != player) return;
        if ((now - lastAttackTime) >= ATTACK_TIMEOUT) return;

        String playerName = player.getName().getString();
        String totemName = getTotemName(player);

        Long lastPop = lastPopTime.get(playerName);
        if (lastPop != null && now - lastPop > POP_RESET_TIME) {
            playerPopCounts.put(playerName, 0);
        }

        int popCount = playerPopCounts.getOrDefault(playerName, 0) + 1;
        playerPopCounts.put(playerName, popCount);
        lastPopTime.put(playerName, now);

        String message = playerName + " | " + totemName;
        notifications.add(new TotemNotification(message, playerName, popCount, now));
    }

    /** Достаём отображаемое имя тотема: сначала offhand, потом mainhand, иначе дефолт. */
    private String getTotemName(PlayerEntity player) {
        ItemStack offhand = player.getOffHandStack();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) {
            String name = offhand.getName().getString();
            if (!name.equals("Тотем бессмертия") && !name.equals("Totem of Undying")) return name;
            return "Тотем бессмертия";
        }
        ItemStack mainhand = player.getMainHandStack();
        if (mainhand.getItem() == Items.TOTEM_OF_UNDYING) {
            String name = mainhand.getName().getString();
            if (!name.equals("Тотем бессмертия") && !name.equals("Totem of Undying")) return name;
            return "Тотем бессмертия";
        }
        return "Тотем";
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
        for (TotemNotification n : notifications) {
            String text = n.message + " §cпоп!";
            if (showPopCount.isValue()) {
                text += " §7(x" + n.popCount + ")";
            }
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, text, 4, y, textColor.getColor());
            y += 12;
        }
    }

    public List<TotemNotification> getNotifications() {
        return notifications;
    }

    public static class TotemNotification {
        public final String message;
        public final String playerName;
        public final int popCount;
        public final long timestamp;

        public TotemNotification(String message, String playerName, int popCount, long timestamp) {
            this.message = message;
            this.playerName = playerName;
            this.popCount = popCount;
            this.timestamp = timestamp;
        }
    }
}
