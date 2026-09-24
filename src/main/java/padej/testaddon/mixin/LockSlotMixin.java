package padej.testaddon.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import padej.testaddon.modules.gameplay.LockSlotModule;

/**
 * Блокирует клики по заблокированным слотам хотбара через ClientPlayerInteractionManager#clickSlot().
 */
@Mixin(ClientPlayerInteractionManager.class)
public class LockSlotMixin {

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void onClickSlot(int syncId, int slotId, int button,
                              SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        LockSlotModule module = LockSlotModule.getInstance();
        if (module == null) return;
        if (module.shouldBlockSlotClick(slotId)) {
            ci.cancel();
        }
    }
}
