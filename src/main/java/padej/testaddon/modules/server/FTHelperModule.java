package padej.testaddon.modules.server;

import padej.soup.api.system.font.Fonts;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.DrawEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.GroupSetting;
import padej.soup.api.feature.module.setting.implement.TextSetting;

import padej.testaddon.SoupBetterCategory;

import java.util.*;

public class FTHelperModule extends Module {

    // ── Имена предметов ──────────────────────────────────────────────────────
    private final TextSetting trapkaName         = new TextSetting("ft.trapka.name",    "ft.trapka.desc").setText("трапка");
    private final TextSetting drakonName         = new TextSetting("ft.drakon.name",    "ft.drakon.desc").setText("драконья трапка");
    private final TextSetting dezorName          = new TextSetting("ft.dezor.name",     "ft.dezor.desc").setText("дезориентация");
    private final TextSetting pylName            = new TextSetting("ft.pyl.name",       "ft.pyl.desc").setText("явная пыль");
    private final TextSetting smerchName         = new TextSetting("ft.smerch.name",    "ft.smerch.desc").setText("огненный смерч");
    private final TextSetting plastName          = new TextSetting("ft.plast.name",     "ft.plast.desc").setText("пласт");
    private final TextSetting auraName           = new TextSetting("ft.aura.name",      "ft.aura.desc").setText("божья аура");
    private final TextSetting snezhokName        = new TextSetting("ft.snezhok.name",   "ft.snezhok.desc").setText("снежок");

    // ── Переключатели ────────────────────────────────────────────────────────
    private final BooleanSetting trapkaEnabled   = new BooleanSetting("ft.en.trapka.name",  "").setValue(true);
    private final BooleanSetting drakonEnabled   = new BooleanSetting("ft.en.drakon.name",  "").setValue(true);
    private final BooleanSetting dezorEnabled    = new BooleanSetting("ft.en.dezor.name",   "").setValue(true);
    private final BooleanSetting pylEnabled      = new BooleanSetting("ft.en.pyl.name",     "").setValue(false);
    private final BooleanSetting smerchEnabled   = new BooleanSetting("ft.en.smerch.name",  "").setValue(false);
    private final BooleanSetting snezhokEnabled  = new BooleanSetting("ft.en.snezhok.name", "").setValue(true);

    private final ColorSetting alertColor = new ColorSetting("ft.color.name", "ft.color.desc").value(0xFFFF5722);

    public FTHelperModule() {
        super("module.ft_helper.name", SoupBetterCategory.SERVER);
        setup(
            trapkaEnabled, trapkaName,
            drakonEnabled, drakonName,
            dezorEnabled,  dezorName,
            pylEnabled,    pylName,
            smerchEnabled, smerchName,
            snezhokEnabled, snezhokName,
            auraName, plastName, alertColor
        );
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (mc.player == null) return;

        String hint = detectItem();
        if (hint == null) return;

        MatrixStack m = e.getDrawContext().getMatrices();
        int cx = e.getDrawContext().getScaledWindowWidth() / 2;
        int cy = e.getDrawContext().getScaledWindowHeight() / 2;
        Fonts.getSize(12, Fonts.Type.INTER_BOLD).drawString(m, hint, cx - 30, cy - 30, alertColor.getColor());
    }

    private String detectItem() {
        if (mc.player == null) return null;
        ItemStack main = mc.player.getMainHandStack();
        if (main.isEmpty()) return null;

        String held = main.getName().getString().toLowerCase();
        if (trapkaEnabled.isValue()  && held.contains(trapkaName.getText().toLowerCase()))  return "▶ ТРАПКА ◀";
        if (drakonEnabled.isValue()  && held.contains(drakonName.getText().toLowerCase()))  return "▶ ДРАКОН ◀";
        if (dezorEnabled.isValue()   && held.contains(dezorName.getText().toLowerCase()))   return "▶ ДЕЗОР ◀";
        if (pylEnabled.isValue()     && held.contains(pylName.getText().toLowerCase()))     return "▶ ПЫЛЬ ◀";
        if (smerchEnabled.isValue()  && held.contains(smerchName.getText().toLowerCase()))  return "▶ СМЕРЧ ◀";
        if (snezhokEnabled.isValue() && held.contains(snezhokName.getText().toLowerCase())) return "▶ СНЕЖОК ◀";
        if (held.contains(auraName.getText().toLowerCase()))  return "▶ АУРА ◀";
        if (held.contains(plastName.getText().toLowerCase())) return "▶ ПЛАСТ ◀";
        return null;
    }
}
