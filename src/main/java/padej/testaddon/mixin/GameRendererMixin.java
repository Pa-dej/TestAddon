package padej.testaddon.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import padej.testaddon.modules.gameplay.ZoomModule;

/**
 * Перехватывает GameRenderer#getFov() для применения зума из ZoomModule.
 * priority = 900 — ниже стандартного (1000), чтобы не конфликтовать с миксинами SoupAPI.
 */
@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov,
                          CallbackInfoReturnable<Float> cir) {
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom == null || !zoom.isEnabled()) return;
        cir.setReturnValue(zoom.applyZoom(cir.getReturnValue()));
    }
}
