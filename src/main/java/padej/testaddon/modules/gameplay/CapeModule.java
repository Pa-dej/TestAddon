package padej.testaddon.modules.gameplay;

import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.TextSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.CapeModule}.
 *
 * <p>Список «друзей», которым показывать кастомный плащ. Оригинал хранил
 * массив имён через ConfigManager (count + per-index). В порте список
 * хранится в одном {@link TextSetting} как comma-separated значения —
 * читать удобнее, конфиг проще.</p>
 *
 * <p>Сам рендер плащей выполняется потребителем (mixin/рендер), модуль —
 * только хранилище и API проверки {@link #isFriend(String)}.</p>
 */
public class CapeModule extends Module {

    private static CapeModule INSTANCE;

    private final TextSetting friends = new TextSetting(
            "cape.friends.name", "cape.friends.desc"
    ).setText("").setMax(512);

    public CapeModule() {
        super("module.cape.name", SoupBetterCategory.GAMEPLAY);
        setup(friends);
        INSTANCE = this;
    }

    public static CapeModule instance() { return INSTANCE; }

    public List<String> getFriends() {
        String raw = friends.getText();
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isFriend(String name) {
        if (name == null || name.isEmpty()) return false;
        return getFriends().stream().anyMatch(f -> f.equalsIgnoreCase(name));
    }

    public void addFriend(String name) {
        if (name == null || name.isBlank()) return;
        List<String> list = getFriends();
        if (list.stream().anyMatch(s -> s.equalsIgnoreCase(name))) return;
        list.add(name.trim());
        friends.setText(String.join(",", list));
    }

    public void removeFriend(String name) {
        if (name == null || name.isBlank()) return;
        List<String> list = getFriends();
        list.removeIf(s -> s.equalsIgnoreCase(name));
        friends.setText(String.join(",", list));
    }

    public void clearFriends() {
        friends.setText("");
    }
}
