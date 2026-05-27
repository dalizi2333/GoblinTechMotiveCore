package com.goblincoders.goblintech.api.machine.feature.multiblock;

import com.goblincoders.goblintech.api.machine.multiblock.part.TieredPartGoblinMachine;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;

import net.minecraft.resources.ResourceLocation;

public final class GhostPartHullRenderType {

    public static final DynamicRenderType<TieredPartGoblinMachine, GhostPartHullRender> TYPE =
            GhostPartHullRender.TYPE;

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("goblintech", "ghost_part_hull");

    public static void register() {
        DynamicRenderManager.register(ID, TYPE);
    }

    private GhostPartHullRenderType() {}
}
