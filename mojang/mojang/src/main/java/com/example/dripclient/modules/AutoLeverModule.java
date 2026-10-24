package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.PlaceUtil;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Gdy ktos wyleje wode obok Ciebie, mod od razu stawia w tym miejscu dzwignie (zabierajac zrodlo wody). */
public class AutoLeverModule extends Module {
    private static final int SCAN = 6;

    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.0, 1.0, 4.5, 0.5));
    private final Setting.Bool ignoreBucket = add(new Setting.Bool("Nie działaj z wiadrem wody", true));

    private Set<BlockPos> prev = new HashSet<>();
    private BlockPos prevCenter = null;

    public AutoLeverModule() {
        super("AutoLever", Category.WORLD);
    }

    @Override
    public void onDisable() {
        prev = new HashSet<>();
        prevCenter = null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;

        BlockPos c = mc.player.getBlockPos();
        Set<BlockPos> now = new HashSet<>();
        for (BlockPos pos : BlockPos.iterate(c.add(-SCAN, -SCAN, -SCAN), c.add(SCAN, SCAN, SCAN))) {
            BlockState st = mc.world.getBlockState(pos);
            if (st.isOf(Blocks.WATER) && st.get(FluidBlock.LEVEL) == 0) now.add(pos.toImmutable());
        }

        if (prevCenter != null) {
            List<BlockPos> fresh = new ArrayList<>();
            for (BlockPos pos : now) {
                if (prev.contains(pos)) continue;
                // liczy sie tylko woda, ktora pojawila sie w obszarze skanowanym w poprzednim ticku
                if (Math.abs(pos.getX() - prevCenter.getX()) > SCAN
                        || Math.abs(pos.getY() - prevCenter.getY()) > SCAN
                        || Math.abs(pos.getZ() - prevCenter.getZ()) > SCAN) continue;
                fresh.add(pos);
            }

            boolean holdsBucket = ignoreBucket.value && mc.player.isHolding(Items.WATER_BUCKET);
            int slot = PlaceUtil.findSlot(mc.player, s -> s.isOf(Items.LEVER));
            if (!fresh.isEmpty() && slot >= 0 && !holdsBucket) {
                Vec3d eye = mc.player.getEyePos();
                fresh.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(eye)));
                int done = 0;
                for (BlockPos pos : fresh) {
                    if (done >= 2) break;
                    if (Vec3d.ofCenter(pos).squaredDistanceTo(eye) > range.value * range.value) continue;
                    if (PlaceUtil.placeAt(mc, pos, slot)) done++;
                }
            }
        }
        prev = now;
        prevCenter = c;
    }
}
