package padej.testaddon.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Перенесено 1:1 из {@code winvi.moscow.soupbetter.util.PlayerIntersectionUtil}.
 */
public class PlayerIntersectionUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Stream<Entity> streamEntities() {
        return StreamSupport.stream(mc.world.getEntities().spliterator(), false);
    }

    public static boolean isBoxInBlock(Box box, Block block) {
        return isBox(box, pos -> mc.world.getBlockState(pos).getBlock().equals(block));
    }

    public static boolean isBox(Box box, Predicate<BlockPos> pos) {
        return BlockPos.stream(box).anyMatch(pos);
    }

    public static boolean isAir(BlockPos blockPos) {
        return isAir(mc.world.getBlockState(blockPos));
    }

    public static boolean isAir(BlockState state) {
        return state.isAir() || state.getBlock().equals(Blocks.CAVE_AIR) || state.getBlock().equals(Blocks.VOID_AIR);
    }
}
