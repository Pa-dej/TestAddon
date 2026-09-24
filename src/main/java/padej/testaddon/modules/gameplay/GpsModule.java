package padej.testaddon.modules.gameplay;

import net.minecraft.util.math.BlockPos;
import padej.soup.api.feature.module.Module;
import padej.testaddon.SoupBetterCategory;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.GpsModule}.
 *
 * <p>Простое хранилище одной активной точки-маяка (waypoint). Используется
 * {@link padej.testaddon.modules.server.EventDelayModule} для пометки координат
 * серверных ивентов; внешние рендеры могут читать состояние через
 * {@link #hasWaypoint()}/{@link #getTargetPos()}.</p>
 */
public class GpsModule extends Module {

    private static GpsModule INSTANCE;

    private BlockPos targetPos = null;
    private String waypointName = "";

    public GpsModule() {
        super("module.gps.name", SoupBetterCategory.GAMEPLAY);
        // Без settings — это data-holder модуль; on/off управляет рендером
        // вэйпоинтов потребителями.
        INSTANCE = this;
    }

    public static GpsModule instance() { return INSTANCE; }

    public void setWaypoint(BlockPos pos, String name) {
        this.targetPos    = pos;
        this.waypointName = name;
    }

    public void clearWaypoint() {
        this.targetPos    = null;
        this.waypointName = "";
    }

    public BlockPos getTargetPos()    { return targetPos;    }
    public String   getWaypointName() { return waypointName; }
    public boolean  hasWaypoint()     { return targetPos != null; }
}
