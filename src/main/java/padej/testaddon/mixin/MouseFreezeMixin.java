package padej.testaddon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import padej.testaddon.modules.gameplay.AutoSwapModule;

/**
 * Перехватывает {@code Entity.changeLookDirection(dx, dy)} на head'е и
 * отменяет вызов, пока активен {@link AutoSwapModule#MOUSE_FROZEN} флаг.
 *
 * <p>Метод финальный на {@code Entity}, но из {@code Mouse.updateMouse}
 * он зовётся через тот же символ — Java-компилятор эмитит INVOKEVIRTUAL
 * на классе-объявителе (Entity), а не на ClientPlayerEntity. Поэтому
 * {@code @Redirect} на {@code ClientPlayerEntity.changeLookDirection}
 * не сработал — здесь мы хукаем САМ метод, что надёжнее любого call-site
 * патча.</p>
 *
 * <p>Фильтр {@code this == mc.player} гарантирует, что мы трогаем только
 * клиентского игрока — повороты других сущностей через этот же метод
 * (если такое где-то делается) идут штатно.</p>
 *
 * <p>Альтернатива «snap yaw/pitch каждый тик» вызывала тряску экрана при
 * попытке шевелить мышкой; здесь мы дропаем сам ввод курсора ещё ДО его
 * применения к pitch/yaw — камера статична, экран спокойный.</p>
 */
@Mixin(Entity.class)
public class MouseFreezeMixin {

    @Inject(
            method = "changeLookDirection(DD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!AutoSwapModule.MOUSE_FROZEN) return;
        Entity self = (Entity) (Object) this;
        if (self != MinecraftClient.getInstance().player) return;
        ci.cancel();
    }
}
