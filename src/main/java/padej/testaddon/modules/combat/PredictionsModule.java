package padej.testaddon.modules.combat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.opengl.GL11;
import padej.soup.api.event.EventHandler;
import padej.soup.api.event.events.render.WorldRenderEvent;
import padej.soup.api.feature.module.Module;
import padej.soup.api.feature.module.setting.implement.BooleanSetting;
import padej.soup.api.feature.module.setting.implement.ColorSetting;
import padej.soup.api.feature.module.setting.implement.ValueSetting;
import padej.soup.implement.features.modules.client.Theme;
import padej.testaddon.SoupBetterCategory;
import padej.testaddon.util.PlayerIntersectionUtil;
import padej.testaddon.util.RaytracingUtil;
import padej.testaddon.util.Render3DUtil;
import padej.testaddon.util.rotation.Rotation;
import padej.testaddon.util.rotation.RotationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * Перенос {@code winvi.moscow.soupbetter.modules.PredictionsModule}.
 *
 * <p><b>Назначение модуля</b>: рисует траектории брошенных и потенциально-кидаемых проджектайлов:
 * snowball, egg, ender pearl, trident (при удержании), бутылочка опыта, splash potion,
 * стрелы от лука/арбалета, фейерверки из арбалета. В точках столкновения рисуется
 * горизонтальный круг-индикатор. Используется как «прицел» для расчёта точки падения.</p>
 *
 * <p>Это <i>не</i> HUD-индикатор шанса попадания. Логика 1:1 с оригиналом.</p>
 */
public final class PredictionsModule extends Module {

    private final BooleanSetting predictInHand = new BooleanSetting(
            "predictions.predict_in_hand.name", "predictions.predict_in_hand.desc"
    ).setValue(true);

    private final BooleanSetting predictThrown = new BooleanSetting(
            "predictions.predict_thrown.name", "predictions.predict_thrown.desc"
    ).setValue(true);

    private final ValueSetting lineWidth = new ValueSetting(
            "predictions.line_width.name", "predictions.line_width.desc"
    ).range(1.0f, 6.0f).setValue(2.5f);

    private final ColorSetting overrideColor = new ColorSetting(
            "predictions.color.name", "predictions.color.desc"
    ).value(0x00000000); // 0 alpha = брать helperColor из ThemeModule

    private final List<Point> points = new ArrayList<>();

    public PredictionsModule() {
        super("module.predictions.name", SoupBetterCategory.COMBAT);
        setup(predictInHand, predictThrown, lineWidth, overrideColor);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrix = e.getStack();

        points.clear();

        if (predictInHand.isValue()) {
            drawPredictionInHand(matrix,
                    StreamSupport.stream(mc.player.getHandItems().spliterator(), false).toList());
        }

        if (!predictThrown.isValue()) return;

        int helperColor = getHelperColor();
        float width = lineWidth.getValue();

        getProjectiles().forEach(entity -> {
            Vec3d motion = entity.getVelocity();
            Vec3d pos = entity.getPos();
            Vec3d prevPos;
            int ticks = 0;

            for (int i = 0; i < 300; i++) {
                prevPos = pos;
                pos = pos.add(motion);
                motion = calculateMotion(entity, prevPos, motion);

                HitResult result = RaytracingUtil.raycast(prevPos, pos, RaycastContext.ShapeType.COLLIDER, entity);
                if (!result.getType().equals(HitResult.Type.MISS)) {
                    pos = result.getPos();
                }

                Render3DUtil.drawLine(prevPos, pos, helperColor, width, false);

                Vec3d finalPrevPos = prevPos, finalPos = pos;
                boolean inEntity = PlayerIntersectionUtil.streamEntities()
                        .filter(ent -> ent instanceof LivingEntity living && living != mc.player && living.isAlive())
                        .anyMatch(ent -> ent.getBoundingBox().expand(0.25).intersects(finalPrevPos, finalPos));
                if (result.getType().equals(HitResult.Type.BLOCK) || pos.y < -128 || inEntity
                        || result.getType().equals(HitResult.Type.ENTITY)) {
                    breakingPoint(entity, pos, ticks);
                    break;
                }
                ticks++;
            }
        });
    }

    public void drawPredictionInHand(MatrixStack matrix, List<ItemStack> stacks) {
        if (mc.player == null) return;
        Item activeItem = mc.player.getActiveItem().getItem();
        for (ItemStack stack : stacks) {
            List<HitResult> results = switch (stack.getItem()) {
                case ExperienceBottleItem item ->
                        checkTrajectory(new ExperienceBottleEntity(mc.world, mc.player, stack), 0.8);
                case SplashPotionItem item ->
                        checkTrajectory(new PotionEntity(mc.world, mc.player, stack), 0.55);
                case TridentItem item when item.equals(activeItem) && mc.player.getItemUseTime() >= 10 ->
                        checkTrajectory(new TridentEntity(mc.world, mc.player, stack), 2.5);
                case SnowballItem item ->
                        checkTrajectory(new SnowballEntity(mc.world, mc.player, stack), 1.5);
                case EggItem item ->
                        checkTrajectory(new EggEntity(mc.world, mc.player, stack), 1.5);
                case EnderPearlItem item ->
                        checkTrajectory(new EnderPearlEntity(mc.world, mc.player, stack), 1.5);
                case BowItem item when item.equals(activeItem) && mc.player.isUsingItem() ->
                        checkTrajectory(new ArrowEntity(mc.world, mc.player, stack, stack),
                                3 * MathHelper.clamp(
                                        (mc.player.getItemUseTime() + mc.getRenderTickCounter().getTickDelta(false)) / 20F,
                                        0F, 1F));
                case CrossbowItem item when CrossbowItem.isCharged(stack) -> {
                    ChargedProjectilesComponent component = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
                    List<HitResult> list = new ArrayList<>();
                    if (component != null) {
                        float velocity = component.getProjectiles().getFirst().isOf(Items.FIREWORK_ROCKET) ? 100 : 3;
                        list.add(checkTrajectory(RotationUtil.getClientRotation().toVector(),
                                new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                        if (component.getProjectiles().size() > 2) {
                            float pitchAbs = mc.player.getPitch() / 90;
                            float delta = pitchAbs * pitchAbs * pitchAbs * pitchAbs * pitchAbs;
                            float yaw = MathHelper.lerp(Math.abs(delta), 10, 90);
                            float pitch = MathHelper.lerp(delta, 0, 10);
                            list.add(checkTrajectory(
                                    new Rotation(mc.player.getYaw() - yaw, mc.player.getPitch() - pitch).toVector(),
                                    new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                            list.add(checkTrajectory(
                                    new Rotation(mc.player.getYaw() + yaw, mc.player.getPitch() - pitch).toVector(),
                                    new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                        }
                    }
                    yield list;
                }
                default -> null;
            };
            if (results != null) {
                results = results.stream().filter(Objects::nonNull).toList();
                if (!results.isEmpty()) renderProjectileResults(matrix, results);
            }
            // оригинал имеет return после первой итерации — сохраняем
            return;
        }
    }

    public void renderProjectileResults(MatrixStack matrix, List<HitResult> results) {
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(lineWidth.getValue() + 1.0f);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        int helperColor = getHelperColor();

        for (HitResult result : results) {
            Direction direction = getDirection(result);
            Vec3d renderPos = result.getPos().subtract(mc.getEntityRenderDispatcher().camera.getPos());
            double width = 0.4;

            matrix.push();
            matrix.translate(renderPos.x, renderPos.y, renderPos.z);
            if (direction.equals(Direction.WEST) || direction.equals(Direction.EAST))
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
            else if (direction.equals(Direction.SOUTH) || direction.equals(Direction.NORTH))
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            for (int i = 0, size = 90; i <= size; i++) {
                Vec3d v1 = cosSin(i, size, width);
                Vec3d v2 = cosSin(i + 1, size, width);
                Render3DUtil.vertexLine(matrix, buffer, v1, v2, helperColor);
            }
            Render3DUtil.vertexLine(matrix, buffer, new Vec3d(0, 0, -width), new Vec3d(0, 0, width), helperColor);
            Render3DUtil.vertexLine(matrix, buffer, new Vec3d(-width, 0, 0), new Vec3d(width, 0, 0), helperColor);
            matrix.pop();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
    }

    private Vec3d cosSin(int i, int size, double width) {
        int index = Math.min(i, size);
        double PI2 = Math.PI * 2;
        float cos = (float) (Math.cos(index * PI2 / size) * width);
        float sin = (float) (-Math.sin(index * PI2 / size) * width);
        return new Vec3d(cos, 0, sin);
    }

    public List<Entity> getProjectiles() {
        return PlayerIntersectionUtil.streamEntities()
                .filter(e -> {
                    if (!(e instanceof PersistentProjectileEntity || e instanceof ThrownItemEntity)) return false;
                    if (visible(e)) return false;
                    if (e instanceof ProjectileEntity projectile) {
                        Entity owner = projectile.getOwner();
                        return owner != null && owner.equals(mc.player);
                    }
                    return false;
                })
                .toList();
    }

    public List<HitResult> checkTrajectory(ProjectileEntity entity, double velocity) {
        return new ArrayList<>(Collections.singleton(
                checkTrajectory(RotationUtil.getClientRotation().toVector(), entity, velocity)));
    }

    public HitResult checkTrajectory(Vec3d lookVec, ProjectileEntity entity, double velocity) {
        double distance = Math.sqrt(lookVec.x * lookVec.x + lookVec.y * lookVec.y + lookVec.z * lookVec.z);
        Vec3d motion = mc.player.getPos().subtract(mc.player.prevX, mc.player.prevY, mc.player.prevZ);
        if (entity instanceof ArrowEntity arrow && arrow.getItemStack().getItem() instanceof CrossbowItem) {
            motion = Vec3d.ZERO;
        }
        Vec3d interpolated = interpolate(mc.player);
        return traceTrajectory(
                mc.player.getEyePos().add(interpolated.subtract(mc.player.getPos())),
                lookVec.multiply(velocity / distance).add(motion),
                entity);
    }

    private Vec3d interpolate(Entity entity) {
        if (entity == null) return Vec3d.ZERO;
        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        return new Vec3d(
                MathHelper.lerp(tickDelta, entity.prevX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.prevY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ())
        );
    }

    public HitResult calcTrajectory(ProjectileEntity e) {
        return traceTrajectory(e.getPos(), e.getVelocity(), e);
    }

    public HitResult traceTrajectory(Vec3d pos, Vec3d motion, ProjectileEntity entity) {
        Vec3d prevPos;
        for (int i = 0; i < 300; i++) {
            prevPos = pos;
            pos = pos.add(motion);
            motion = calculateMotion(entity, prevPos, motion);

            HitResult result = RaytracingUtil.raycast(prevPos, pos, RaycastContext.ShapeType.COLLIDER, entity);
            if (!result.getType().equals(HitResult.Type.MISS)) {
                return result;
            }

            Vec3d finalPrevPos = prevPos, finalPos = pos;
            if (PlayerIntersectionUtil.streamEntities()
                    .filter(ent -> ent != entity.getOwner() && ent instanceof LivingEntity living && living != mc.player && living.isAlive())
                    .anyMatch(ent -> ent.getBoundingBox().expand(0.3).intersects(finalPrevPos, finalPos))) {
                return new HitResult(pos) {
                    @Override
                    public Type getType() {
                        return Type.ENTITY;
                    }
                };
            }
            if (pos.y < -128) break;
        }
        return null;
    }

    public Vec3d calculateMotion(Entity entity, Vec3d prevPos, Vec3d motion) {
        boolean isInWater = Objects.requireNonNull(mc.world)
                .getBlockState(BlockPos.ofFloored(prevPos))
                .getFluidState().isIn(FluidTags.WATER);

        float multiply = switch (entity) {
            case TridentEntity i -> 0.99F;
            case PersistentProjectileEntity i when isInWater -> 0.6F;
            default -> isInWater ? 0.8F : 0.99F;
        };

        return motion.multiply(multiply).add(0, -entity.getFinalGravity(), 0);
    }

    private void breakingPoint(Entity entity, Vec3d pos, int ticks) {
        switch (entity) {
            case ItemEntity item -> points.add(new Point(item.getStack(), pos, ticks));
            case ThrownItemEntity thrown -> points.add(new Point(thrown.getStack(), pos, ticks));
            case PersistentProjectileEntity persistent -> points.add(new Point(persistent.getItemStack(), pos, ticks));
            default -> {
            }
        }
    }

    private Direction getDirection(HitResult result) {
        if (result instanceof BlockHitResult blockHitResult) {
            return blockHitResult.getSide();
        }
        Vec3d diff = result.getPos().subtract(mc.player.getEyePos()).normalize();
        return Direction.getFacing(diff.x, diff.y, diff.z);
    }

    private boolean visible(Entity entity) {
        boolean posChange = entity.getX() == entity.prevX && entity.getY() == entity.prevY && entity.getZ() == entity.prevZ;
        boolean itemEntityCheck = entity instanceof ItemEntity
                && (entity.isOnGround()
                || PlayerIntersectionUtil.isBoxInBlock(entity.getBoundingBox().expand(2), Blocks.WATER));
        return posChange || itemEntityCheck;
    }

    /**
     * Если пользователь не задал свой цвет (alpha=0), берём mainGuiColor из
     * SoupVisuals.Theme. У этой темы нет отдельного «helper»-цвета — это
     * сознательное упрощение по сравнению с SoupBetterExample.
     */
    private int getHelperColor() {
        int c = overrideColor.getColorWithAlpha();
        if ((c >>> 24) == 0) {
            try {
                Theme theme = Theme.getInstance();
                if (theme != null) return theme.mainGuiColor.getColorWithAlpha();
            } catch (Throwable ignored) {}
            return 0xFF6496FF;
        }
        return c;
    }

    private record Point(ItemStack stack, Vec3d pos, int ticks) {
    }
}
