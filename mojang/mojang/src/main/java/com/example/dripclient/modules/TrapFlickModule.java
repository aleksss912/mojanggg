package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Klapy w zasiegu otwieraja sie i zamykaja bez przerwy. */
public class TrapFlickModule extends Module {
    private final Setting.Num interval = add(new Setting.Num("Co ile ticków", 4, 1, 20, 1));
    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.0, 1.0, 4.5, 0.5));

    private int cooldown = 0;

    public TrapFlickModule() {
        super("Szybkie klapy", Category.WORLD);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (++cooldown < interval.value) return;
        cooldown = 0;

        ClientPlayerEntity p = mc.player;
        if (p.isSneaking()) return;

        ClientWorld w = mc.world;
        Vec3d eye = p.getEyePos();
        double r = range.value;
        int ri = (int) Math.ceil(r);
        BlockPos base = p.getBlockPos();

        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(base.add(-ri, -ri, -ri), base.add(ri, ri, ri))) {
            BlockState st = w.getBlockState(pos);
            if (!(st.getBlock() instanceof TrapdoorBlock) || st.isOf(Blocks.IRON_TRAPDOOR)) continue;
            if (Vec3d.ofCenter(pos).squaredDistanceTo(eye) > r * r) continue;
            found.add(pos.toImmutable());
        }
        found.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(eye)));

        int n = 0;
        for (BlockPos pos : found) {
            if (n++ >= 4) break;
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        }
    }
}
