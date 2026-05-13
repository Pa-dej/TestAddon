package padej.testaddon.modules.server;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.chat.ReceiveChatMessageEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.testaddon.SoupBetterCategory;
import padej.testaddon.modules.gameplay.GpsModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.EventDelayModule}.
 *
 * <p><b>Назначение</b>: парсит чат-сообщения с анонсами серверных ивентов
 * (Сундук смерти, Маяк убийца, Дед мороз и т.д.) и автоматически создаёт
 * waypoint в {@link GpsModule}. Поддерживает «delay»-логику: если в одном
 * сообщении пришло название без координат, ждём до 5 секунд следующего
 * сообщения с координатами и склеиваем их в один waypoint.</p>
 *
 * <p>Поддержка двух источников: <b>FunTime</b> (по списку ключевых слов) и
 * <b>Holyworld</b> (любое сообщение с координатами).</p>
 *
 * <p>Это <i>не</i> «задержать отправку команды» — название «Delay» в оригинале
 * относилось к временному окну склейки name↔coords между сообщениями.</p>
 */
public class EventDelayModule extends Module {

    private static final long EVENT_TIMEOUT = 5000L;

    private static final Pattern COORDS_PATTERN          = Pattern.compile("\\[?(-?\\d+)[,\\s]+(-?\\d+)[,\\s]+(-?\\d+)\\]?");
    private static final Pattern COORDS_BRACKETS_PATTERN = Pattern.compile("\\[(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]");

    private static final List<String> FUNTIME_EVENT_KEYWORDS = Arrays.asList(
            "Сундук смерти",
            "Сундуки",
            "Маяк убийца",
            "Загадочный маяк",
            "Дед мороз",
            "Адская резня",
            "Мистический алтарь",
            "Вулкан",
            "Метеоритный дождь"
    );

    private final BooleanSetting funtimeSource = new BooleanSetting(
            "event_delay.funtime.name", "event_delay.funtime.desc"
    ).setValue(true);

    private final BooleanSetting holyworldSource = new BooleanSetting(
            "event_delay.holyworld.name", "event_delay.holyworld.desc"
    ).setValue(true);

    public record Waypoint(String name, BlockPos pos, long timestamp) {}

    private final List<Waypoint> waypoints = new ArrayList<>();
    private String lastEventName = "";
    private long lastEventTime = 0L;

    public EventDelayModule() {
        super("module.event_delay.name", SoupBetterCategory.SERVER);
        setup(funtimeSource, holyworldSource);
    }

    @EventHandler
    public void onChat(ReceiveChatMessageEvent e) {
        Text message = e.getMessage();
        if (message == null) return;
        onChatMessage(message);
    }

    void onChatMessage(Text message) {
        if (mc.player == null || mc.getCurrentServerEntry() == null) return;

        String serverAddress = mc.getCurrentServerEntry().address.toLowerCase();
        String text = message.getString();

        Matcher matcher = COORDS_PATTERN.matcher(text);
        boolean hasCoords = matcher.find();
        if (!hasCoords) {
            matcher = COORDS_BRACKETS_PATTERN.matcher(text);
            hasCoords = matcher.find();
        }

        boolean shouldProcess = false;
        String waypointName = "";

        boolean isFunTime = serverAddress.contains(".funtime.") || serverAddress.contains("funtime.");
        boolean isHolyworld = serverAddress.contains(".holyworld.") || serverAddress.contains("holyworld.");

        if (funtimeSource.isValue() && isFunTime) {
            for (String keyword : FUNTIME_EVENT_KEYWORDS) {
                if (text.contains(keyword)) {
                    if (hasCoords) {
                        shouldProcess = true;
                        waypointName = keyword;
                    } else {
                        // имя пришло без координат — сохраняем и ждём следующего сообщения
                        lastEventName = keyword;
                        lastEventTime = System.currentTimeMillis();
                        return;
                    }
                    break;
                }
            }
            if (!shouldProcess && hasCoords) {
                long timeSinceLastEvent = System.currentTimeMillis() - lastEventTime;
                if (!lastEventName.isEmpty() && timeSinceLastEvent < EVENT_TIMEOUT) {
                    shouldProcess = true;
                    waypointName = lastEventName;
                    lastEventName = "";
                }
            }
        } else if (holyworldSource.isValue() && isHolyworld) {
            if (hasCoords) {
                shouldProcess = true;
                waypointName = extractWaypointName(text);
            }
        }

        if (!shouldProcess) return;

        try {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));

            BlockPos pos = new BlockPos(x, y, z);
            for (Waypoint existing : waypoints) {
                if (existing.pos.equals(pos)) return;
            }
            if (waypointName.isEmpty()) waypointName = "Event " + x + " " + y + " " + z;

            Waypoint waypoint = new Waypoint(waypointName, pos, System.currentTimeMillis());
            waypoints.add(waypoint);

            GpsModule gps = GpsModule.instance();
            if (gps != null) gps.setWaypoint(pos, waypointName);
        } catch (NumberFormatException ignored) {
        }
    }

    private String extractWaypointName(String text) {
        String name = text.replaceAll(COORDS_PATTERN.pattern(), "").trim();
        name = name.replaceAll("\\s+", " ");
        name = name.replaceAll("[\\[\\](){}]", "").trim();
        if (name.isEmpty()) return "";
        if (name.length() > 30) name = name.substring(0, 30) + "...";
        return name;
    }

    public List<Waypoint> getWaypoints() {
        return new ArrayList<>(waypoints);
    }

    public void removeWaypoint(int index) {
        if (index < 0 || index >= waypoints.size()) return;
        Waypoint removed = waypoints.remove(index);
        GpsModule gps = GpsModule.instance();
        if (gps != null && gps.hasWaypoint() && gps.getTargetPos().equals(removed.pos)) {
            gps.clearWaypoint();
        }
    }

    public void clearAllWaypoints() {
        waypoints.clear();
        GpsModule gps = GpsModule.instance();
        if (gps != null) gps.clearWaypoint();
    }

    public void setActiveWaypoint(int index) {
        if (index < 0 || index >= waypoints.size()) return;
        Waypoint w = waypoints.get(index);
        GpsModule gps = GpsModule.instance();
        if (gps != null) gps.setWaypoint(w.pos, w.name);
    }
}
