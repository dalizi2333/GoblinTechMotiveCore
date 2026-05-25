package com.goblincoders.goblintech;

import com.goblincoders.goblintech.api.GoblinTechValues;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GoblinTech {

    public static final String NAME = "GoblinTechMotive";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static boolean isClientSide() {
        return FMLEnvironment.dist.isClient();
    }

    public static class Mods {

        public static boolean isLDLibLoaded() {
            return isModLoaded(GoblinTechValues.MODID_LDLIB);
        }

        public static boolean isConfigurationLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CONFIGURATION);
        }

        public static boolean isAE2Loaded() {
            return isModLoaded(GoblinTechValues.MODID_APPENG);
        }

        public static boolean isCreateLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CREATE);
        }

        public static boolean isPonderLoaded() {
            return isModLoaded(GoblinTechValues.MODID_PONDER);
        }

        public static boolean isFlywheelLoaded() {
            return isModLoaded(GoblinTechValues.MODID_FLYWHEEL);
        }

        public static boolean isNeoEcoAELoaded() {
            return isModLoaded(GoblinTechValues.MODID_NEOECOAE);
        }

        public static boolean isLDLib2Loaded() {
            return isModLoaded(GoblinTechValues.MODID_LDLIB2);
        }

        public static boolean isSableLoaded() {
            return isModLoaded(GoblinTechValues.MODID_SABLE);
        }

        public static boolean isCAeroLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CAERO);
        }

        public static boolean isCreateBigCannonsLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CREATEBIGCANNONS);
        }

        public static boolean isDriveByWireLoaded() {
            return isModLoaded(GoblinTechValues.MODID_DRIVEBYWIRE);
        }

        public static boolean isDeployerLoaded() {
            return isModLoaded(GoblinTechValues.MODID_DEPLOYER);
        }

        public static boolean isVSHoseConnectorsLoaded() {
            return isModLoaded(GoblinTechValues.MODID_VSHOSECONNECTORS);
        }

        public static boolean isCreateMetallurgyLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CREATEMETALLURGY);
        }

        public static boolean isCreateDieselGeneratorsLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CREATEDIESELGENERATORS);
        }

        public static boolean isCreateTweakedControllersLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CREATETWEAKEDCONTROLLERS);
        }

        public static boolean isRPLLoaded() {
            return isModLoaded(GoblinTechValues.MODID_RPL);
        }

        public static boolean isCreatePropulsionSimulatedLoaded() {
            return isModLoaded(GoblinTechValues.MODID_CREATEPROPULSIONSIMULATED);
        }

        public static boolean isCEELoaded() {
            return isModLoaded(GoblinTechValues.MODID_CEE);
        }

        public static boolean isPatchouliLoaded() {
            return isModLoaded(GoblinTechValues.MODID_PATCHOULI);
        }

        public static boolean isTFCLoaded() {
            return isModLoaded(GoblinTechValues.MODID_TFC);
        }

        public static boolean isFirmaLifeLoaded() {
            return isModLoaded(GoblinTechValues.MODID_FIRMALIFE);
        }

        public static boolean isTorqueLinkLoaded() {
            return isModLoaded(GoblinTechValues.MODID_TORQUELINK);
        }

        public static boolean isXrayLoaded() {
            return isModLoaded(GoblinTechValues.MODID_ADVANCEDXRAY);
        }
    }
}
