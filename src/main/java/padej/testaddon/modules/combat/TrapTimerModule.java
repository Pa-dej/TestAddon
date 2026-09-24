package padej.testaddon.modules.combat;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.SelectSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.soup.api.system.font.Fonts;
import padej.testaddon.SoupBetterCategory;

import java.util.Locale;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.TrapTimerModule}.
 *
 * <p><b>Назначение</b>: отсчитывает таймер действия серверных «трапок»
 * (NETHERITE_SCRAP с custom-name «трапка», FunTime/AresMine). Два типа:
 * обычная (15 сек) и драконья (20 сек).</p>
 *
 * <p>Триггеры старта:
 * <ul>
 *     <li><b>Авто</b> (если включено {@link #autoStart}) — по факту использования
 *     трапки: отслеживается уменьшение количества предметов с name=«трапка»
 *     в руке (рост use-cooldown'а аналогично event'у {@code onItemUse}).</li>
 *     <li><b>Ручной</b> — на bind-клавишу {@link #startKey}.</li>
 * </ul></p>
 *
 * <p>Имя трапки настраивается через {@link #trapName} (по умолчанию «трапка»).</p>
 */
public class TrapTimerModule extends Module {

    public enum TrapType {
        NORMAL("Обычная", 15_000),
        DRAGON("Драконья", 20_000);

        public final String displayName;
        public final int durationMs;
        TrapType(String n, int d) { displayName = n; durationMs = d; }
    }

    private final SelectSetting trapType = new SelectSetting(
            "trap_timer.type.name", "trap_timer.type.desc"
    ).value("normal", "dragon").selected("normal");

    private final BooleanSetting autoStart = new BooleanSetting(
            "trap_timer.auto_start.name", "trap_timer.auto_start.desc"
    ).setValue(true);

    private final TextSetting trapName = new TextSetting(
            "trap_timer.name.name", "trap_timer.name.desc"
    ).setText("трапка").setMax(48).visible(autoStart::isValue);

    private final BindSetting startKey = new BindSetting(
            "trap_timer.start.name", "trap_timer.start.desc"
    );

    private final BindSetting resetKey = new BindSetting(
            "trap_timer.reset.name", "trap_timer.reset.desc"
    );

    private final ColorSetting activeColor = new ColorSetting(
            "trap_timer.active_color.name", "trap_timer.active_color.desc"
    ).value(0xFF4CAF50);

    private final ColorSetting expiredColor = new ColorSetting(
            "trap_timer.expired_color.name", "trap_timer.expired_color.desc"
    ).value(0xFFF44336);

    private long startMs = -1;
    private int prevTrapCount = -1;

    public TrapTimerModule() {
        super("module.trap_timer.name", SoupBetterCategory.COMBAT);
        setup(trapType, autoStart, trapName, startKey, resetKey, activeColor, expiredColor);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(startKey.getKey())) startMs = System.currentTimeMillis();
        if (e.isKeyDown(resetKey.getKey())) startMs = -1;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) {
            prevTrapCount = -1;
            return;
        }
        if (!autoStart.isValue()) {
            prevTrapCount = -1;
            return;
        }

        int curCount = countTrapsInInventory();
        if (prevTrapCount >= 0 && curCount < prevTrapCount) {
            // Использовали трапку → запускаем таймер
            startMs = System.currentTimeMillis();
        }
        prevTrapCount = curCount;
    }

    private int countTrapsInInventory() {
        var player = mc.player;
        if (player == null) return 0;
        String target = trapName.getText().toLowerCase(Locale.ROOT);
        if (target.isEmpty()) return 0;
        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.getItem() != Items.NETHERITE_SCRAP) continue;
            if (s.getName().getString().toLowerCase(Locale.ROOT).contains(target)) {
                count += s.getCount();
            }
        }
        return count;
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (startMs < 0) return;
        MatrixStack m = e.getDrawContext().getMatrices();
        TrapType type = trapType.isSelected("dragon") ? TrapType.DRAGON : TrapType.NORMAL;
        long elapsed = System.currentTimeMillis() - startMs;
        long remaining = type.durationMs - elapsed;
        if (remaining <= 0) {
            Fonts.getSize(12, Fonts.Type.INTER_BOLD)
                    .drawString(m, "§c" + type.displayName + ": ИСТЕКЛА", 4, 50, expiredColor.getColor());
        } else {
            String time = String.format("%.1f", remaining / 1000.0);
            Fonts.getSize(12, Fonts.Type.INTER_BOLD)
                    .drawString(m, type.displayName + ": §a" + time + "s", 4, 50, activeColor.getColor());
        }
    }
}
