package padej.testaddon.modules.gameplay;

import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.keyboard.KeyEvent;
import padej.soup.api.event.events.keyboard.MouseRotationEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BindSetting;
import padej.testaddon.SoupBetterCategory;
import org.lwjgl.glfw.GLFW;

public class FreelookModule extends Module {

    private final BindSetting key = new BindSetting(
            "freelook.key.name", "freelook.key.desc"
    );

    private static FreelookModule INSTANCE;

    private boolean active = false;
    private float anchorYaw, anchorPitch;
    private float lookYaw, lookPitch;
    private float prevLookYaw, prevLookPitch;
    private Perspective originalPerspective = Perspective.FIRST_PERSON;

    public FreelookModule() {
        super("module.freelook.name", SoupBetterCategory.GAMEPLAY, false, false);
        setup(key);
        INSTANCE = this;
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        int k = key.getKey();
        if (k == GLFW.GLFW_KEY_UNKNOWN || mc.player == null) return;

        if (e.isKeyDown(k) && !active) {
            activate_freelook();
        } else if (!e.isKeyDown(k) && active) {
            deactivate_freelook();
        }
    }

    @EventHandler
    public void onMouseRotation(MouseRotationEvent e) {
        if (!active || mc.player == null) return;
        double sens = mc.options.getMouseSensitivity().getValue();
        double scaled = sens * 0.6 + 0.2;
        double step = scaled * scaled * scaled * 8.0;

        prevLookYaw = lookYaw;
        prevLookPitch = lookPitch;
        lookYaw += (float)(e.getCursorDeltaX() * step * 0.15);
        lookPitch = MathHelper.clamp(lookPitch + (float)(e.getCursorDeltaY() * step * 0.15), -90f, 90f);
        e.cancel();
    }

    private void activate_freelook() {
        if (mc.player == null || mc.currentScreen != null) return;
        anchorYaw = mc.player.getYaw();
        anchorPitch = mc.player.getPitch();
        lookYaw = anchorYaw;
        lookPitch = anchorPitch;
        prevLookYaw = lookYaw;
        prevLookPitch = lookPitch;
        originalPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        active = true;
    }

    private void deactivate_freelook() {
        if (!active) return;
        if (mc.player != null) {
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        }
        mc.options.setPerspective(originalPerspective);
        active = false;
    }

    @Override
    public void deactivate() { deactivate_freelook(); }

    public boolean isActive() { return active; }
    public float getCameraYaw(float td) { return MathHelper.lerpAngleDegrees(td, prevLookYaw, lookYaw); }
    public float getCameraPitch(float td) { return MathHelper.lerp(td, prevLookPitch, lookPitch); }
    public float getAnchorYaw() { return anchorYaw; }
    public float getAnchorPitch() { return anchorPitch; }

    public static FreelookModule getInstance() { return INSTANCE; }
}
