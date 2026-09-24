package padej.testaddon.modules.combat;

import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.SnowballTrackerModule}.
 *
 * <p><b>Назначение</b>: отслеживает <i>конкретные</i> снежки сервера
 * (по умолчанию — «снежок заморозки», замораживающие снаряды FunTime/AresMine).
 * Фильтр — {@code entity.getName().toLowerCase().contains(targetName)}.
 * Это <i>не</i> «все снежки в мире», это серверно-специфичный трекер.</p>
 *
 * <p>API сохранён: {@link #trackSnowball(SnowballEntity)} — вызывается mixin'ом
 * на спавне SnowballEntity. {@link #getTrackedSnowballs()} читают рендеры/другие модули.</p>
 */
public class SnowballTrackerModule extends Module {

    private static SnowballTrackerModule INSTANCE;

    private final TextSetting targetName = new TextSetting(
            "snowball_tracker.target.name", "snowball_tracker.target.desc"
    ).setText("снежок заморозки").setMax(64);

    private final List<TrackedSnowball> trackedSnowballs = new ArrayList<>();

    public SnowballTrackerModule() {
        super("module.snowball_tracker.name", SoupBetterCategory.COMBAT);
        setup(targetName);
        INSTANCE = this;
    }

    public static SnowballTrackerModule getInstance() { return INSTANCE; }

    /** Вызывается mixin'ом на entity-spawn'е. */
    public void trackSnowball(SnowballEntity snowball) {
        if (!isEnabled()) return;
        String name = snowball.getName().getString().toLowerCase(Locale.ROOT);
        String target = targetName.getText().toLowerCase(Locale.ROOT);
        if (!target.isEmpty() && name.contains(target)) {
            trackedSnowballs.add(new TrackedSnowball(snowball));
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        // Удаляем умершие/удалённые снежки. Если модуль выключен — список просто чистим.
        if (!isEnabled()) {
            trackedSnowballs.clear();
            return;
        }
        Iterator<TrackedSnowball> it = trackedSnowballs.iterator();
        while (it.hasNext()) {
            TrackedSnowball t = it.next();
            if (t.snowball.isRemoved() || !t.snowball.isAlive()) it.remove();
        }
    }

    public List<TrackedSnowball> getTrackedSnowballs() {
        return trackedSnowballs;
    }

    public String getTargetName() { return targetName.getText(); }

    public static class TrackedSnowball {
        public final SnowballEntity snowball;

        public TrackedSnowball(SnowballEntity snowball) {
            this.snowball = snowball;
        }

        public Vec3d getPosition() {
            return snowball.getPos();
        }

        public BlockPos getBlockPos() {
            return snowball.getBlockPos();
        }
    }
}
