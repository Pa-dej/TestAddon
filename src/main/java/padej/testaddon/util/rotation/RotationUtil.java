package padej.testaddon.util.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import static java.lang.Math.hypot;
import static java.lang.Math.toDegrees;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

/**
 * Перенесено 1:1 из {@code winvi.moscow.soupbetter.util.rotation.RotationUtil}.
 */
public class RotationUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Rotation getClientRotation() {
        return new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    public static Rotation fromVec3d(Vec3d vector) {
        return new Rotation(
                (float) wrapDegrees(toDegrees(Math.atan2(vector.z, vector.x)) - 90),
                (float) wrapDegrees(toDegrees(-Math.atan2(vector.y, hypot(vector.x, vector.z))))
        );
    }

    public static Rotation calculateAngle(Vec3d to) {
        return fromVec3d(to.subtract(mc.player.getEyePos()));
    }
}
