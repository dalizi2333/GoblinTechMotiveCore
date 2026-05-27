package com.goblincoders.goblintech.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

public class TieredPartGoblinMachine extends TieredPartMachine {

    @Getter
    @SaveField
    @SyncToClient
    @RerenderOnChanged
    private BlockState hullState;

    @Getter
    @SaveField(nbtKey = "originalBlock")
    private ItemStack originalBlockStack;

    public TieredPartGoblinMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.hullState = Blocks.STONE.defaultBlockState();
        this.originalBlockStack = ItemStack.EMPTY;
    }

    public void setHullState(BlockState hullState) {
        this.hullState = hullState;
        syncDataHolder.markClientSyncFieldDirty("hullState");
    }

    public void setOriginalBlockStack(ItemStack stack) {
        this.originalBlockStack = stack;
        syncDataHolder.markClientSyncFieldDirty("originalBlockStack");
    }

    @Override
    @MustBeInvokedByOverriders
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        if (!originalBlockStack.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                    getLevel(),
                    getBlockPos().getX() + 0.5,
                    getBlockPos().getY() + 0.5,
                    getBlockPos().getZ() + 0.5,
                    originalBlockStack.copy());
            getLevel().addFreshEntity(itemEntity);
        }
    }
}
