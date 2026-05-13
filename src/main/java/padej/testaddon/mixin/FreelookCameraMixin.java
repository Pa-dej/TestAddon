package padej.testaddon.mixin;

import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import padej.testaddon.modules.gameplay.FreelookModule;

/**
 * Переопределяет Camera#setRotation() при активном FreelookModule,
 * подставляя кастомные yaw/pitch камеры вместо yaw/pitch игрока.
 * priority = 900 — не конфликтует с CameraMixin SoupAPI (priority = 1000).
 */
@Mixin(value = Camera.class, priority = 900)
public class FreelookCameraMixin {

    @Shadow private float pitch;
    @Shadow private float yaw;
    @Shadow private Quaternionf rotation;
    @Shadow private Vector3f horizontalPlane;
    @Shadow private Vector3f verticalPlane;
    @Shadow private Vector3f diagonalPlane;

    @Inject(method = "setRotation", at = @At("HEAD"), cancellable = true)
    private void onSetRotation(float entityYaw, float entityPitch, CallbackInfo ci) {
        FreelookModule freelook = FreelookModule.getInstance();
        if (freelook == null || !freelook.isActive()) return;

        float camYaw   = freelook.getCameraYaw(1.0f);
        float camPitch = freelook.getCameraPitch(1.0f);

        // Воспроизводим логику Camera#setRotation с подменёнными углами
        this.yaw   = camYaw;
        this.pitch = camPitch;
        this.rotation.identity()
                .rotateY((float) Math.PI + camYaw   * ((float) Math.PI / 180f))
                .rotateX(-camPitch * ((float) Math.PI / 180f));
        this.horizontalPlane.set(0f, 0f, 1f).rotate(this.rotation);
        this.verticalPlane  .set(0f, 1f, 0f).rotate(this.rotation);
        this.diagonalPlane  .set(1f, 0f, 0f).rotate(this.rotation);

        ci.cancel();
    }
}
