package padej.testaddon.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import padej.testaddon.modules.combat.ItemHighlighterModule;
import padej.testaddon.modules.server.AuctionHelperModule;

/**
 * Рисует цветной оверлей поверх слотов инвентаря для двух модулей:
 *  - ItemHighlighterModule: подсвечивает тотемы, зелья, незеритовую броню
 *  - AuctionHelperModule:   подсвечивает лоты по выбранному фильтру
 *
 * Инъекция в TAIL, чтобы оверлей рисовался поверх стандартного фона слота.
 */
@Mixin(HandledScreen.class)
public class ItemHighlightMixin {

    /** Отступ экрана инвентаря от левого края окна (Yarn: HandledScreen#x). */
    @Shadow protected int x;
    /** Отступ экрана инвентаря от верхнего края окна (Yarn: HandledScreen#y). */
    @Shadow protected int y;

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        int absX = this.x + slot.x;
        int absY = this.y + slot.y;

        // --- ItemHighlighterModule ---
        ItemHighlighterModule highlighter = ItemHighlighterModule.getInstance();
        if (highlighter != null && highlighter.isEnabled()) {
            int color = highlighter.getSlotHighlightColor(stack);
            if (color != 0) {
                context.fill(absX, absY, absX + 16, absY + 16, color);
            }
        }

        // --- AuctionHelperModule ---
        AuctionHelperModule auction = AuctionHelperModule.getInstance();
        if (auction != null && auction.shouldHighlight(stack)) {
            context.fill(absX, absY, absX + 16, absY + 16, auction.getHighlightColor());
        }
    }
}
