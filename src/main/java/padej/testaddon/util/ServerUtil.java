package padej.testaddon.util;

import net.minecraft.client.MinecraftClient;

/**
 * Перенесено 1:1 из {@code winvi.moscow.soupbetter.util.ServerUtil}.
 * В оригинале проверка адреса была закомментирована — оба метода всегда возвращают {@code true}.
 * Сохраняем то же поведение.
 */
public class ServerUtil {

    public static boolean isFunTimeServer() {
        // Оригинальная логика (адрес сервера) была закомментирована в SoupBetterExample.
        // Сохраняем дефолт = true. При необходимости — раскомментировать ниже.
        //
        // MinecraftClient mc = MinecraftClient.getInstance();
        // if (mc == null || mc.getNetworkHandler() == null) return false;
        // var serverInfo = mc.getNetworkHandler().getServerInfo();
        // if (serverInfo == null) return false;
        // String addr = serverInfo.address.toLowerCase();
        // return addr.contains("funtime") || addr.contains("funsky");
        return true;
    }

    public static boolean isAresMineServer() {
        // Аналогично — оставлено всегда true.
        return true;
    }
}
