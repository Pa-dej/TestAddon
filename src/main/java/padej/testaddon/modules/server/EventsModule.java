package padej.testaddon.modules.server;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.event.events.world.PlayerJoinWorldEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;

import padej.testaddon.SoupBetterCategory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class EventsModule extends Module {

    public record Event(String name, String time, String coords, String extra) {}

    private final BooleanSetting showFuntime   = new BooleanSetting("events.funtime.name",   "events.funtime.desc").setValue(true);
    private final BooleanSetting showHolyworld = new BooleanSetting("events.holyworld.name", "events.holyworld.desc").setValue(true);

    private final ValueSetting updateInterval = new ValueSetting(
            "events.interval.name", "events.interval.desc"
    ).range(10, 300).setValue(60.0f);

    private final List<Event> eventList = new CopyOnWriteArrayList<>();
    private volatile String statusText = "Нет данных";
    private long lastFetchMs = 0;

    public EventsModule() {
        super("module.events.name", SoupBetterCategory.SERVER);
        setup(showFuntime, showHolyworld, updateInterval);
    }

    @EventHandler
    public void onJoin(PlayerJoinWorldEvent e) {
        fetchAsync();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastFetchMs > updateInterval.getValue() * 1000L) {
            lastFetchMs = now;
            fetchAsync();
        }
    }

    private void fetchAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                // Заглушка: в реальной реализации делается HTTP-запрос к API сервера
                // URL url = new URL("https://api.funtime.su/events");
                // ... парсинг JSON ...
                statusText = "Обновлено: " + new java.text.SimpleDateFormat("HH:mm").format(new Date());
            } catch (Exception ex) {
                statusText = "Ошибка загрузки";
            }
        });
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        MatrixStack m = e.getDrawContext().getMatrices();
        int x = 4, y = 4;
        Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "§lИвенты §r§7(" + statusText + ")", x, y, 0xFFFFFFFF);
        y += 14;
        if (eventList.isEmpty()) {
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "§7Нет активных ивентов", x, y, 0xFF888888);
            return;
        }
        for (Event ev : eventList) {
            Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "§a" + ev.name() + " §7" + ev.time(), x, y, 0xFFFFFFFF);
            if (!ev.coords().isBlank())
                Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, "  §7" + ev.coords(), x, y + 11, 0xFF888888);
            y += 22;
        }
    }
}
