package com.goblincoders.goblintech.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;

import org.jetbrains.annotations.Nullable;

public final class GhostPartHullModel {

    private GhostPartHullModel() {}

    public static MachineBuilder.ModelInitializer createModel(ResourceLocation overlay,
                                                              @Nullable ResourceLocation pipeOverlay,
                                                              @Nullable ResourceLocation emissiveOverlay) {
        return (ctx, prov, builder) -> {
            builder.forAllStatesModels(state -> {
                BlockModelBuilder model = GTMachineModels.colorOverlayHullModel(
                        overlay, pipeOverlay, emissiveOverlay, state, prov.models());
                return GTMachineModels.tieredHullTextures(model, builder.getOwner().getTier());
            });
            builder.addReplaceableTextures("bottom", "top", "side");
            builder.addDynamicRenderer(GhostPartHullRender::new);
        };
    }

    public static MachineBuilder.ModelInitializer createModel(String overlay,
                                                              @Nullable String pipeOverlay,
                                                              @Nullable String emissiveOverlay) {
        ResourceLocation overlayTex = ResourceLocation.fromNamespaceAndPath("goblintech",
                "block/overlay/machine/" + overlay);
        ResourceLocation pipeOverlayTex = pipeOverlay == null ? null :
                ResourceLocation.fromNamespaceAndPath("goblintech", "block/overlay/machine/" + pipeOverlay);
        ResourceLocation emissiveOverlayTex = emissiveOverlay == null ? null :
                ResourceLocation.fromNamespaceAndPath("goblintech", "block/overlay/machine/" + emissiveOverlay);
        return createModel(overlayTex, pipeOverlayTex, emissiveOverlayTex);
    }
}
