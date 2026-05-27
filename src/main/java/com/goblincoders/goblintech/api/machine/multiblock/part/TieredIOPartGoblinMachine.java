package com.goblincoders.goblintech.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import lombok.Getter;

public class TieredIOPartGoblinMachine extends TieredPartGoblinMachine implements IControllable {

    protected final IO io;

    @Getter
    @SaveField
    @SyncToClient
    @RerenderOnChanged
    protected boolean workingEnabled;

    public TieredIOPartGoblinMachine(BlockEntityCreationInfo info, int tier, IO io) {
        super(info, tier);
        this.io = io;
        this.workingEnabled = true;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        syncDataHolder.markClientSyncFieldDirty("workingEnabled");
    }
}
