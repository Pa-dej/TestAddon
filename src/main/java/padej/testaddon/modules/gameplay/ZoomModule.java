package padej.testaddon.modules.gameplay;

import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;
import org.lwjgl.glfw.GLFW;

public class ZoomModule extends Module {

    private final ValueSetting zoomLevel = new ValueSetting(
            "zoom.level.name", "zoom.level.desc"
    ).range(2, 20).setValue(4.0f);

    private final BooleanSetting smooth = new BooleanSetting(
            "zoom.smooth.name", "zoom.smooth.desc"
    ).setValue(true);

    private final ValueSetting smoothSpeed = new ValueSetting(
            "zoom.smooth_speed.name", "zoom.smooth_speed.desc"
    ).range(50, 500).setValue(150.0f)
     .visible(smooth::isValue);

    private final BindSetting key = new BindSetting(
            "zoom.key.name", "zoom.key.desc"
    );

    private static ZoomModule INSTANCE;

    private boolean zoomActive = false;
    private float currentMultiplier = 1.0f;
    private long lastUpdateMs = System.currentTimeMillis();

    public ZoomModule() {
        super("module.zoom.name", SoupBetterCategory.GAMEPLAY, false, false);
        setup(zoomLevel, smooth, smoothSpeed, key);
        INSTANCE = this;
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        int k = key.getKey();
        if (k == GLFW.GLFW_KEY_UNKNOWN) return;
        if (e.isKeyDown(k)) zoomActive = true;
        else if (!e.isKeyDown(k) && zoomActive) zoomActive = false;
    }

    @Override
    public void deactivate() {
        zoomActive = false;
        currentMultiplier = 1.0f;
    }

    /** Вызывается из мixin GameRendererMixin для изменения FOV. */
    public float applyZoom(float baseFov) {
        long now = System.currentTimeMillis();
        float target = zoomActive ? zoomLevel.getValue() : 1.0f;

        if (!smooth.isValue()) {
            currentMultiplier = target;
        } else {
            long delta = Math.max(1L, now - lastUpdateMs);
            float factor = Math.min(1.0f, delta / Math.max(50f, smoothSpeed.getValue()));
            currentMultiplier += (target - currentMultiplier) * factor * 3.0f;
            if (Math.abs(target - currentMultiplier) < 0.01f) currentMultiplier = target;
        }
        lastUpdateMs = now;
        return baseFov / Math.max(1.0f, currentMultiplier);
    }

    public boolean isZoomActive() { return zoomActive; }

    public static ZoomModule getInstance() { return INSTANCE; }
}
