package padej.testaddon.modules.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.player.TickEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.testaddon.SoupBetterCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.PvpAiModule}.
 *
 * <p><b>Назначение</b>: оценивает «силу» нашего игрока и каждого видимого
 * противника, рассчитывает индивидуальные шансы дуэли и средний шанс победы
 * против всех противников в радиусе. Также ведёт фид «damage popup'ов» —
 * чисел урона над хитбоксами тех, кому только что нанесли урон.</p>
 *
 * <p>Все веса в {@code ARMOR_WEIGHTS}/{@code WEAPON_WEIGHTS}, формула
 * {@code solveChance}, {@code SNAPSHOT_RANGE=50}/{@code LABEL_RANGE=32},
 * {@code POPUP_LIFETIME_MS=1150} — 1:1 с оригиналом.</p>
 *
 * <p>Модуль публикует данные через {@link #getThreatLabels()},
 * {@link #getDamagePopups()}, {@link #getAverageWinChance()},
 * {@link #getVisibleOpponentCount()} — рендер выполняет внешний потребитель.</p>
 */
public final class PvpAiModule extends Module {

    public static final double SNAPSHOT_RANGE = 50.0;
    public static final double LABEL_RANGE = 32.0;
    public static final long POPUP_LIFETIME_MS = 1150L;

    private static final List<WeightedKey> ARMOR_WEIGHTS = List.of(
            new WeightedKey("netherite", 4.0f),
            new WeightedKey("diamond", 3.0f),
            new WeightedKey("iron", 2.0f),
            new WeightedKey("gold", 1.5f),
            new WeightedKey("chainmail", 1.5f),
            new WeightedKey("leather", 1.0f)
    );

    private static final List<WeightedKey> WEAPON_WEIGHTS = List.of(
            new WeightedKey("netherite_axe", 12.0f),
            new WeightedKey("diamond_axe", 11.0f),
            new WeightedKey("iron_axe", 9.0f),
            new WeightedKey("netherite_sword", 10.0f),
            new WeightedKey("diamond_sword", 9.0f),
            new WeightedKey("iron_sword", 7.0f),
            new WeightedKey("stone_sword", 6.0f),
            new WeightedKey("golden_sword", 5.0f),
            new WeightedKey("wooden_sword", 5.0f),
            new WeightedKey("trident", 10.0f)
    );

    private static PvpAiModule INSTANCE;

    private final BooleanSetting damageNumbers = new BooleanSetting(
            "pvp_ai.damage_numbers.name", "pvp_ai.damage_numbers.desc"
    ).setValue(true);

    private final ValueSetting damageScalePercent = new ValueSetting(
            "pvp_ai.damage_scale.name", "pvp_ai.damage_scale.desc"
    ).range(50, 200).setValue(100.0f).setInteger(true);

    private final Map<UUID, Float> duelChanceByPlayer = new HashMap<>();
    private final Map<LivingEntity, Float> trackedHealth = new HashMap<>();
    private final List<DamagePopup> damagePopups = new ArrayList<>();

    private float averageWinChance = 50.0f;
    private int visibleOpponentCount = 0;

    public PvpAiModule() {
        super("module.pvp_ai.name", SoupBetterCategory.COMBAT);
        setup(damageNumbers, damageScalePercent);
        INSTANCE = this;
    }

    public static PvpAiModule getInstance() { return INSTANCE; }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) {
            clearRuntimeState();
            return;
        }
        refreshThreatMap();
        refreshDamageFeed();
        damagePopups.removeIf(DamagePopup::expired);
    }

    private void refreshThreatMap() {
        PlayerEntity self = mc.player;
        float selfPower = ratePlayer(self);
        float opponentPowerSum = 0.0f;
        int scannedOpponents = 0;
        Set<UUID> activeIds = new HashSet<>();

        for (PlayerEntity candidate : mc.world.getPlayers()) {
            if (!isTrackedOpponent(candidate, SNAPSHOT_RANGE)) continue;

            float opponentPower = ratePlayer(candidate);
            float duelChance = solveChance(selfPower, opponentPower);

            duelChanceByPlayer.put(candidate.getUuid(), duelChance);
            activeIds.add(candidate.getUuid());
            opponentPowerSum += opponentPower;
            scannedOpponents++;
        }

        duelChanceByPlayer.keySet().removeIf(id -> !activeIds.contains(id));
        visibleOpponentCount = scannedOpponents;
        averageWinChance = scannedOpponents == 0
                ? 50.0f
                : solveChance(selfPower, opponentPowerSum / scannedOpponents);
    }

    private void refreshDamageFeed() {
        if (!damageNumbers.isValue()) {
            trackedHealth.clear();
            damagePopups.clear();
            return;
        }
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || living.isRemoved() || !living.isAlive()) continue;

            float currentHealth = living.getHealth() + living.getAbsorptionAmount();
            Float previousHealth = trackedHealth.put(living, currentHealth);
            if (previousHealth == null) continue;

            float damage = previousHealth - currentHealth;
            if (damage <= 0.05f) continue;

            double sideOffset = (living.getId() & 1) == 0 ? -0.16 : 0.16;
            Vec3d anchor = living.getPos().add(sideOffset, living.getHeight() + 0.30, 0.0);
            damagePopups.add(new DamagePopup(anchor, damage, System.currentTimeMillis()));
        }
        trackedHealth.entrySet().removeIf(entry -> {
            LivingEntity living = entry.getKey();
            return living.isRemoved() || !living.isAlive() || living.getWorld() != mc.world;
        });
    }

    private float ratePlayer(PlayerEntity player) {
        float power = 4.0f;
        float healthRatio = MathHelper.clamp(
                (player.getHealth() + player.getAbsorptionAmount()) / Math.max(1.0f, player.getMaxHealth()),
                0.0f, 2.0f
        );
        power += healthRatio * 20.0f;
        power += player.getHungerManager().getFoodLevel() * 0.5f;
        power += rateArmor(player);

        ItemStack weapon = player.getMainHandStack();
        if (!weapon.isEmpty()) {
            power += rateWeapon(weapon);
            power += rateEnchantments(weapon);
        }
        power += rateEffects(player);
        return Math.max(1.0f, power);
    }

    private float rateArmor(PlayerEntity player) {
        float armorScore = 0.0f;
        float durabilitySum = 0.0f;
        int measuredPieces = 0;
        for (ItemStack armorStack : player.getArmorItems()) {
            if (armorStack.isEmpty()) continue;
            armorScore += lookupWeight(itemId(armorStack), ARMOR_WEIGHTS, 0.5f);
            armorScore += rateEnchantments(armorStack);
            if (armorStack.isDamageable() && armorStack.getMaxDamage() > 0) {
                float durability = 1.0f - (float) armorStack.getDamage() / armorStack.getMaxDamage();
                durabilitySum += durability;
                measuredPieces++;
            }
        }
        if (measuredPieces == 0) return armorScore;
        return armorScore * (durabilitySum / measuredPieces);
    }

    private float rateWeapon(ItemStack stack) {
        return lookupWeight(itemId(stack), WEAPON_WEIGHTS, 2.0f);
    }

    private float rateEffects(PlayerEntity player) {
        float score = 0.0f;
        score += effectWeight(player, StatusEffects.STRENGTH, 5.0f);
        score += effectWeight(player, StatusEffects.RESISTANCE, 5.0f);
        score += effectWeight(player, StatusEffects.SPEED, 3.0f);
        score += effectWeight(player, StatusEffects.REGENERATION, 4.0f);
        score -= effectWeight(player, StatusEffects.WEAKNESS, 3.0f);
        score -= effectWeight(player, StatusEffects.SLOWNESS, 2.0f);
        return score;
    }

    private float effectWeight(PlayerEntity player, RegistryEntry<StatusEffect> effect, float perLevel) {
        var instance = player.getStatusEffect(effect);
        return instance == null ? 0.0f : (instance.getAmplifier() + 1) * perLevel;
    }

    private float rateEnchantments(ItemStack stack) {
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null) return 0.0f;
        float power = 0.0f;
        for (RegistryEntry<Enchantment> enchantment : component.getEnchantments()) {
            int level = component.getLevel(enchantment);
            power += enchantmentWeight(enchantment, level);
        }
        return power;
    }

    private float enchantmentWeight(RegistryEntry<Enchantment> enchantment, int level) {
        if (enchantment.matchesKey(Enchantments.PROTECTION))     return level * 2.0f;
        if (enchantment.matchesKey(Enchantments.SHARPNESS))      return level * 2.5f;
        if (enchantment.matchesKey(Enchantments.FIRE_ASPECT))    return level * 1.5f;
        if (enchantment.matchesKey(Enchantments.KNOCKBACK))      return level;
        if (enchantment.matchesKey(Enchantments.THORNS))         return level * 1.5f;
        if (enchantment.matchesKey(Enchantments.BLAST_PROTECTION)
                || enchantment.matchesKey(Enchantments.PROJECTILE_PROTECTION)
                || enchantment.matchesKey(Enchantments.FIRE_PROTECTION))
            return level * 1.5f;
        if (enchantment.matchesKey(Enchantments.FEATHER_FALLING)
                || enchantment.matchesKey(Enchantments.UNBREAKING))
            return level * 0.5f;
        return level * 0.5f;
    }

    private float solveChance(float ourPower, float enemyPower) {
        float total = ourPower + enemyPower;
        if (total <= 0.0f) return 50.0f;
        return MathHelper.clamp(ourPower / total * 100.0f, 5.0f, 95.0f);
    }

    private boolean isTrackedOpponent(PlayerEntity player, double range) {
        if (mc.player == null || player == null || player == mc.player) return false;
        if (player.isSpectator() || player.isInvisible() || player.isRemoved() || !player.isAlive()) return false;
        return mc.player.distanceTo(player) <= range;
    }

    private float lookupWeight(String itemId, List<WeightedKey> weights, float fallback) {
        for (WeightedKey weight : weights) {
            if (itemId.contains(weight.needle())) return weight.weight();
        }
        return fallback;
    }

    private String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
    }

    private void clearRuntimeState() {
        duelChanceByPlayer.clear();
        trackedHealth.clear();
        damagePopups.clear();
        averageWinChance = 50.0f;
        visibleOpponentCount = 0;
    }

    public List<ThreatLabel> getThreatLabels() {
        if (mc.player == null || mc.world == null) return List.of();
        List<ThreatLabel> labels = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isTrackedOpponent(player, LABEL_RANGE)) continue;
            Float winChance = duelChanceByPlayer.get(player.getUuid());
            if (winChance != null) labels.add(new ThreatLabel(player, winChance));
        }
        return labels;
    }

    public List<DamagePopup> getDamagePopups() {
        return damagePopups.isEmpty() ? List.of() : List.copyOf(damagePopups);
    }

    public float getAverageWinChance() { return averageWinChance; }
    public int getVisibleOpponentCount() { return visibleOpponentCount; }
    public int getDamageScalePercent() { return (int) damageScalePercent.getValue(); }
    public boolean isDamageNumbersEnabled() { return damageNumbers.isValue(); }

    private record WeightedKey(String needle, float weight) {}

    public record ThreatLabel(PlayerEntity player, float winChance) {}

    public record DamagePopup(Vec3d anchor, float damage, long bornAt) {
        public boolean expired() {
            return System.currentTimeMillis() - bornAt > POPUP_LIFETIME_MS;
        }

        public float progress(long now) {
            return MathHelper.clamp((now - bornAt) / (float) POPUP_LIFETIME_MS, 0.0f, 1.0f);
        }

        public Vec3d positionAt(long now) {
            float progress = progress(now);
            double drift = Math.sin((anchor.x + anchor.z) * 6.0 + progress * 7.0) * 0.05;
            return anchor.add(drift, 0.22 + progress * 0.95, 0.0);
        }

        public float alphaAt(long now) {
            float progress = progress(now);
            if (progress <= 0.68f) return 1.0f;
            return 1.0f - (progress - 0.68f) / 0.32f;
        }

        public float scaleAt(long now, int percent) {
            float progress = progress(now);
            float introScale = progress < 0.18f ? 0.78f + (progress / 0.18f) * 0.36f : 1.14f;
            return introScale * (percent / 100.0f);
        }
    }
}
