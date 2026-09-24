package padej.testaddon.modules.gameplay;

import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.PickaxeNotificationsModule}.
 *
 * <p><b>Назначение</b>: при копании, если активная кирка в main-hand имеет
 * оставшуюся прочность ниже порога — присылаем сообщение в чат и (если
 * включено {@code autoSwitch}) переключаемся на настроенный hotbar-слот.</p>
 *
 * <p>Отличия от оригинала: вместо отдельного {@code onAttackBlock} хука
 * (которого нет в SoupAPI) используется тик-проверка состояния
 * {@code interactionManager.isBreakingBlock()} — семантика та же.
 * {@code durabilityThreshold} — абсолютное число оставшейся прочности,
 * <i>не процент</i>.</p>
 */
public class PickaxeNotificationsModule extends Module {

    private static final int MIN_HOTBAR_SLOT = 1;
    private static final int MAX_HOTBAR_SLOT = 9;

    private final ValueSetting durabilityThreshold = new ValueSetting(
            "pickaxe_notif.threshold.name", "pickaxe_notif.threshold.desc"
    ).range(10, 1000).setValue(100.0f).setInteger(true);

    private final BooleanSetting autoSwitch = new BooleanSetting(
            "pickaxe_notif.auto_switch.name", "pickaxe_notif.auto_switch.desc"
    ).setValue(false);

    private final ValueSetting swapSlot = new ValueSetting(
            "pickaxe_notif.swap_slot.name", "pickaxe_notif.swap_slot.desc"
    ).range(MIN_HOTBAR_SLOT, MAX_HOTBAR_SLOT).setValue(1.0f).setInteger(true)
            .visible(autoSwitch::isValue);

    private String warnedSignature;
    private boolean wasBreakingBlock;

    public PickaxeNotificationsModule() {
        super("module.pickaxe_notifications.name", SoupBetterCategory.GAMEPLAY);
        setup(durabilityThreshold, autoSwitch, swapSlot);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.interactionManager == null) {
            warnedSignature = null;
            wasBreakingBlock = false;
            return;
        }

        boolean breakingNow = mc.interactionManager.isBreakingBlock();
        // Триггеримся в момент начала копания (rising-edge) и продолжаем,
        // пока копаем — но повторно warn'имся только если изменился signature.
        if (!breakingNow) {
            wasBreakingBlock = false;
            return;
        }
        // Каждый тик копания → проверка
        wasBreakingBlock = true;

        ItemStack stack = mc.player.getMainHandStack();
        if (!isTrackedPickaxe(stack)) {
            warnedSignature = null;
            return;
        }

        int remainingDurability = stack.getMaxDamage() - stack.getDamage();
        if (remainingDurability >= durabilityThreshold.getValue()) {
            warnedSignature = null;
            return;
        }

        String currentSignature = buildStackSignature(stack);
        if (currentSignature.equals(warnedSignature)) return;
        warnedSignature = currentSignature;

        boolean switched = false;
        if (autoSwitch.isValue()) switched = switchToConfiguredSlot();
        sendLowDurabilityMessage(remainingDurability, switched);
    }

    private boolean isTrackedPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageable() && stack.getItem() instanceof PickaxeItem;
    }

    private String buildStackSignature(ItemStack stack) {
        return mc.player.getInventory().selectedSlot + ":" + System.identityHashCode(stack);
    }

    private boolean switchToConfiguredSlot() {
        int targetSlot = clampHotbarSlot((int) swapSlot.getValue()) - 1;
        if (mc.player.getInventory().selectedSlot == targetSlot) return false;
        mc.player.getInventory().selectedSlot = targetSlot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
        }
        return true;
    }

    private void sendLowDurabilityMessage(int remainingDurability, boolean switched) {
        MutableText message = Text.literal("Pickaxe durability is low").formatted(Formatting.RED);
        message.append(Text.literal(" (" + remainingDurability + " left)").formatted(Formatting.GOLD));
        if (switched) {
            int s = clampHotbarSlot((int) swapSlot.getValue());
            message.append(Text.literal(" -> slot " + s).formatted(Formatting.GRAY));
        }
        logDirect(message);
    }

    private int clampHotbarSlot(int slot) {
        return Math.max(MIN_HOTBAR_SLOT, Math.min(MAX_HOTBAR_SLOT, slot));
    }
}
