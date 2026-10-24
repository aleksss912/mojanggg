package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.PlaceUtil;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Obklada dzwignie w zasiegu: najpierw pajeczyna w komorce, w ktora dzwignia "wystaje",
 * a potem zwykly blok tuz za pajeczyna.
 */
public class ErrorKillerModule extends Module {
    private final Setting.Bool web = add(new Setting.Bool("Pajęczyna przy dźwigni", true));
    private final Setting.Bool block = add(new Setting.Bool("Blok za pajęczyną", true));
    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.0, 1.0, 4.5, 0.5));
    private final Setting.Num delay = add(new Setting.Num("Opóźnienie (ticki)", 2, 1, 10, 1));

    private int cooldown = 0;

    public ErrorKillerModule() {
        super("ErrorKiller", Category.WORLD);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.currentScreen != null) return;
        if (++cooldown < delay.value) return;
        cooldown = 0;

        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        Vec3d eye = p.getEyePos();

        int webSlot = PlaceUtil.findSlot(p, s -> s.isOf(Items.COBWEB));
        int blockSlot = PlaceUtil.findSlot(p, s -> s.getItem() instanceof BlockItem bi
                && bi.getBlock() != Blocks.COBWEB
                && bi.getBlock() != Blocks.LEVER
                && !(bi.getBlock() instanceof FallingBlock));

        int r = (int) Math.ceil(range.value);
        BlockPos base = p.getBlockPos();
        List<BlockPos> levers = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
            if (!w.getBlockState(pos).isOf(Blocks.LEVER)) continue;
            if (Vec3d.ofCenter(pos).squaredDistanceTo(eye) > range.value * range.value) continue;
            levers.add(pos.toImmutable());
        }
        levers.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(eye)));

        int actions = 0;
        for (BlockPos pos : levers) {
            if (actions >= 2) break;
            BlockState st = w.getBlockState(pos);
            BlockFace face = st.get(Properties.BLOCK_FACE);
            Direction out = switch (face) {
                case FLOOR -> Direction.UP;
                case CEILING -> Direction.DOWN;
                default -> st.get(Properties.HORIZONTAL_FACING);
            };
            BlockPos front = pos.offset(out);
            BlockPos beyond = front.offset(out);

            if (web.value && webSlot >= 0 && w.getBlockState(front).isAir()) {
                if (PlaceUtil.placeAt(mc, front, webSlot)) {
                    actions++;
                    continue;
                }
            }
            if (block.value && blockSlot >= 0
                    && w.getBlockState(front).isOf(Blocks.COBWEB)
                    && w.getBlockState(beyond).isAir()) {
                if (PlaceUtil.placeAt(mc, beyond, blockSlot)) actions++;
            }
        }
    }
}
