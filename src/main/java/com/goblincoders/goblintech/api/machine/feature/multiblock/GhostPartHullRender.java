package com.goblincoders.goblintech.api.machine.feature.multiblock;

import com.goblincoders.goblintech.api.machine.multiblock.part.TieredPartGoblinMachine;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gregtechceu.gtceu.client.util.GTQuadTransformers;
import com.gregtechceu.gtceu.client.util.ModelUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GhostPartHullRender extends DynamicRender<TieredPartGoblinMachine, GhostPartHullRender> {

    public static final MapCodec<GhostPartHullRender> CODEC = MapCodec.unit(GhostPartHullRender::new);
    public static final DynamicRenderType<TieredPartGoblinMachine, GhostPartHullRender> TYPE =
            new DynamicRenderType<>(GhostPartHullRender.CODEC);

    public GhostPartHullRender() {}

    @Override
    public DynamicRenderType<TieredPartGoblinMachine, GhostPartHullRender> getType() {
        return TYPE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @NotNull List<BakedQuad> getRenderQuads(@Nullable TieredPartGoblinMachine machine,
                                                    @Nullable BlockAndTintGetter level,
                                                    @Nullable BlockPos pos,
                                                    @Nullable BlockState blockState,
                                                    @Nullable Direction side,
                                                    RandomSource rand,
                                                    @NotNull ModelData modelData,
                                                    @Nullable RenderType renderType) {
        if (machine == null || level == null || pos == null) return List.of();

        BlockState hullState = machine.getHullState();
        if (hullState == null || hullState.isAir() || hullState == Blocks.STONE.defaultBlockState()) {
            return List.of();
        }

        var bakedModel = ModelUtils.getModelForState(hullState);
        ModelData extraData = bakedModel.getModelData(level, pos, hullState, modelData);

        List<BakedQuad> srcQuads = bakedModel.getQuads(hullState, side, rand, extraData, renderType);
        if (srcQuads.isEmpty()) return List.of();

        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        List<BakedQuad> result = new ArrayList<>(srcQuads.size());

        for (BakedQuad quad : srcQuads) {
            BakedQuad processed = GTQuadTransformers.copy(quad);
            if (quad.isTinted()) {
                int color = blockColors.getColor(hullState, level, pos, quad.getTintIndex());
                processed = GTQuadTransformers.setColor(processed, color, true);
            }
            result.add(processed);
        }

        return result;
    }

    @Override
    public void render(TieredPartGoblinMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {}

    @Override
    public boolean isBlockEntityRenderer() {
        return false;
    }
}
